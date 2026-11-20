-- Aplicacion manual alternativa a Flyway. Requiere migraciones anteriores.
BEGIN;
SET LOCAL search_path TO public, pg_catalog;
CREATE TABLE IF NOT EXISTS dian_transporte_configuracion (
    id BIGINT PRIMARY KEY CHECK(id=1),
    modo VARCHAR(20) NOT NULL CHECK(modo IN ('SIMULACION','HABILITACION')),
    version BIGINT NOT NULL DEFAULT 0
);
INSERT INTO dian_transporte_configuracion(id,modo) VALUES(1,'SIMULACION') ON CONFLICT DO NOTHING;
CREATE TABLE IF NOT EXISTS dian_archivos_consecutivos (
    id VARCHAR(100) PRIMARY KEY,
    siguiente BIGINT NOT NULL CHECK(siguiente BETWEEN 1 AND 4294967296)
);
CREATE TABLE IF NOT EXISTS dian_envios (
    id UUID PRIMARY KEY,
    documento_id UUID NOT NULL REFERENCES documentos_fiscales(id),
    origen_documento_id UUID NOT NULL REFERENCES documentos_fiscales(id),
    clave_logica VARCHAR(240) NOT NULL,
    modo VARCHAR(20) NOT NULL CHECK(modo IN ('SIMULACION','HABILITACION')),
    ambiente VARCHAR(20) NOT NULL CHECK(ambiente='PRUEBAS'),
    emisor_nit VARCHAR(10) NOT NULL,
    numero VARCHAR(80) NOT NULL,
    tipo_xml VARCHAR(20) NOT NULL,
    cufe_cude VARCHAR(96) NOT NULL,
    test_set_id UUID NOT NULL,
    archivo_zip VARCHAR(80) NOT NULL,
    archivo_xml VARCHAR(80) NOT NULL,
    contenido_zip BYTEA NOT NULL,
    huella_zip VARCHAR(64) NOT NULL,
    certificado_huella VARCHAR(64) NOT NULL,
    alcance_firma VARCHAR(80) NOT NULL,
    estado VARCHAR(20) NOT NULL CHECK(estado IN ('PREPARADO','EN_CURSO','RECIBIDO','PENDIENTE','ACEPTADO','RECHAZADO','INCIERTO','CANCELADO')),
    track_id VARCHAR(200),
    mensaje VARCHAR(2000),
    respuesta_json TEXT,
    consultas INTEGER NOT NULL DEFAULT 0,
    propietario UUID,
    creado_en TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    actualizado_en TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    version BIGINT NOT NULL DEFAULT 0,
    UNIQUE(documento_id,modo),
    UNIQUE(modo,emisor_nit,archivo_zip)
);
CREATE INDEX IF NOT EXISTS idx_dian_envios_clave ON dian_envios(clave_logica,creado_en DESC);
CREATE INDEX IF NOT EXISTS idx_dian_envios_origen ON dian_envios(origen_documento_id);
CREATE TABLE IF NOT EXISTS dian_intercambios (
    id UUID PRIMARY KEY,
    envio_id UUID NOT NULL REFERENCES dian_envios(id),
    accion VARCHAR(30) NOT NULL,
    solicitud_xml TEXT NOT NULL,
    respuesta_xml TEXT,
    http_status INTEGER,
    diagnostico VARCHAR(2000),
    creado_en TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP
);
CREATE INDEX IF NOT EXISTS idx_dian_intercambios_envio ON dian_intercambios(envio_id,creado_en);
CREATE OR REPLACE FUNCTION proteger_paquete_dian() RETURNS trigger LANGUAGE plpgsql AS $$
BEGIN
    IF TG_OP='DELETE' THEN RAISE EXCEPTION 'Conserve los paquetes DIAN y su trazabilidad.'; END IF;
    IF (NEW.id,NEW.documento_id,NEW.origen_documento_id,NEW.clave_logica,NEW.modo,NEW.ambiente,NEW.emisor_nit,NEW.numero,NEW.tipo_xml,NEW.cufe_cude,NEW.test_set_id,NEW.archivo_zip,NEW.archivo_xml,NEW.contenido_zip,NEW.huella_zip,NEW.certificado_huella,NEW.alcance_firma,NEW.creado_en)
       IS DISTINCT FROM
       (OLD.id,OLD.documento_id,OLD.origen_documento_id,OLD.clave_logica,OLD.modo,OLD.ambiente,OLD.emisor_nit,OLD.numero,OLD.tipo_xml,OLD.cufe_cude,OLD.test_set_id,OLD.archivo_zip,OLD.archivo_xml,OLD.contenido_zip,OLD.huella_zip,OLD.certificado_huella,OLD.alcance_firma,OLD.creado_en)
    THEN RAISE EXCEPTION 'El paquete DIAN y su destino son inmutables.'; END IF;
    RETURN NEW;
END; $$;
DROP TRIGGER IF EXISTS dian_paquete_inmutable ON dian_envios;
CREATE TRIGGER dian_paquete_inmutable BEFORE UPDATE OR DELETE ON dian_envios FOR EACH ROW EXECUTE FUNCTION proteger_paquete_dian();
DROP TRIGGER IF EXISTS dian_intercambio_inmutable ON dian_intercambios;
CREATE TRIGGER dian_intercambio_inmutable BEFORE UPDATE OR DELETE ON dian_intercambios FOR EACH ROW EXECUTE FUNCTION proteger_evidencia_fiscal();

COMMIT;
