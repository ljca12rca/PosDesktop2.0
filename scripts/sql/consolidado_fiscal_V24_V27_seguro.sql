-- Alias de compatibilidad. Preferir consolidado_fiscal_20261119_seguro.sql.
-- Consolidado fiscal 20261119. Requiere esquema POS hasta V23 o superior.
-- Aditivo. No reinicia configuracion, numeros ni datos. No escribe historial Flyway.
-- Si se aplicaron antiguas V24-V27 fiscales, ejecutar primero el puente de compatibilidad.
BEGIN;
SET LOCAL search_path TO public, pg_catalog;
-- V20261119_1__persistencia_fiscal_y_configuracion_dian.sql
-- Aditiva: no modifica ventas, clientes, numeros ni credenciales Dataico existentes.
ALTER TABLE facturas_electronicas ADD COLUMN IF NOT EXISTS proveedor VARCHAR(20) NOT NULL DEFAULT 'DATAICO';

CREATE TABLE IF NOT EXISTS facturacion_configuracion (
    id INTEGER PRIMARY KEY CHECK (id = 1),
    proveedor VARCHAR(20) NOT NULL DEFAULT 'DATAICO' CHECK (proveedor IN ('DATAICO','DIAN_DIRECT')),
    version BIGINT NOT NULL DEFAULT 0
);
INSERT INTO facturacion_configuracion (id) VALUES (1) ON CONFLICT DO NOTHING;

CREATE TABLE IF NOT EXISTS dian_configuraciones (
    ambiente VARCHAR(20) PRIMARY KEY CHECK (ambiente IN ('PRUEBAS','PRODUCCION')),
    datos_json TEXT,
    secretos_cifrados TEXT,
    version BIGINT NOT NULL DEFAULT 0
);
INSERT INTO dian_configuraciones (ambiente) VALUES ('PRUEBAS'),('PRODUCCION') ON CONFLICT DO NOTHING;

CREATE TABLE IF NOT EXISTS documentos_fiscales (
    id UUID PRIMARY KEY,
    venta_id UUID NOT NULL REFERENCES ventas(id),
    cliente_id UUID REFERENCES clientes_electronicos(id),
    proposito VARCHAR(20) NOT NULL CHECK (proposito IN ('BORRADOR','ENVIO')),
    proveedor VARCHAR(20) NOT NULL CHECK (proveedor IN ('DATAICO','DIAN_DIRECT')),
    ambiente VARCHAR(20) NOT NULL,
    cuenta VARCHAR(80) NOT NULL,
    numero VARCHAR(80) NOT NULL,
    formato VARCHAR(40) NOT NULL,
    snapshot_json TEXT NOT NULL,
    xml TEXT,
    huella VARCHAR(64) NOT NULL UNIQUE,
    cliente_nombre VARCHAR(200) NOT NULL,
    total NUMERIC(19,2) NOT NULL,
    creado_en TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP
);
CREATE INDEX IF NOT EXISTS idx_documentos_fiscales_venta ON documentos_fiscales(venta_id);
CREATE INDEX IF NOT EXISTS idx_documentos_fiscales_fecha ON documentos_fiscales(creado_en DESC);
CREATE UNIQUE INDEX IF NOT EXISTS uq_documentos_fiscales_envio_numero
    ON documentos_fiscales(proveedor,cuenta,ambiente,numero) WHERE proposito = 'ENVIO';

CREATE TABLE IF NOT EXISTS facturacion_operaciones (
    venta_id UUID PRIMARY KEY REFERENCES ventas(id),
    cliente_id UUID NOT NULL REFERENCES clientes_electronicos(id),
    proveedor VARCHAR(20) NOT NULL CHECK (proveedor IN ('DATAICO','DIAN_DIRECT')),
    documento_id UUID NOT NULL REFERENCES documentos_fiscales(id),
    estado VARCHAR(30) NOT NULL CHECK (estado IN ('EN_CURSO','CONFIRMADO','INCIERTO','ERROR_SEGURO')),
    propietario UUID NOT NULL,
    intentos INTEGER NOT NULL DEFAULT 1,
    resultado_json TEXT,
    version BIGINT NOT NULL DEFAULT 0,
    actualizado_en TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE TABLE IF NOT EXISTS eventos_fiscales (
    id UUID PRIMARY KEY,
    documento_id UUID NOT NULL REFERENCES documentos_fiscales(id),
    tipo VARCHAR(40) NOT NULL,
    detalle VARCHAR(500) NOT NULL,
    creado_en TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP
);
CREATE INDEX IF NOT EXISTS idx_eventos_fiscales_documento ON eventos_fiscales(documento_id,creado_en);

CREATE OR REPLACE FUNCTION proteger_evidencia_fiscal() RETURNS trigger LANGUAGE plpgsql AS $$
BEGIN
    RAISE EXCEPTION 'La evidencia fiscal es inmutable; registre un nuevo documento o evento.';
END;
$$;
DROP TRIGGER IF EXISTS documentos_fiscales_inmutables ON documentos_fiscales;
CREATE TRIGGER documentos_fiscales_inmutables BEFORE UPDATE OR DELETE ON documentos_fiscales
    FOR EACH ROW EXECUTE FUNCTION proteger_evidencia_fiscal();
DROP TRIGGER IF EXISTS eventos_fiscales_inmutables ON eventos_fiscales;
CREATE TRIGGER eventos_fiscales_inmutables BEFORE UPDATE OR DELETE ON eventos_fiscales
    FOR EACH ROW EXECUTE FUNCTION proteger_evidencia_fiscal();

CREATE OR REPLACE FUNCTION proteger_proveedor_fiscal() RETURNS trigger LANGUAGE plpgsql AS $$
BEGIN
    IF NEW.proveedor IS DISTINCT FROM OLD.proveedor OR NEW.venta_id IS DISTINCT FROM OLD.venta_id THEN
        RAISE EXCEPTION 'No se puede cambiar el proveedor o la venta de un documento fiscal existente.';
    END IF;
    RETURN NEW;
END;
$$;
DROP TRIGGER IF EXISTS operaciones_proveedor_inmutable ON facturacion_operaciones;
CREATE TRIGGER operaciones_proveedor_inmutable BEFORE UPDATE ON facturacion_operaciones
    FOR EACH ROW EXECUTE FUNCTION proteger_proveedor_fiscal();
DROP TRIGGER IF EXISTS facturas_proveedor_inmutable ON facturas_electronicas;
CREATE TRIGGER facturas_proveedor_inmutable BEFORE UPDATE ON facturas_electronicas
    FOR EACH ROW EXECUTE FUNCTION proteger_proveedor_fiscal();

-- V20261119_2__devolucion_anulacion.sql
CREATE TABLE IF NOT EXISTS numeraciones_ajustes (
    id VARCHAR(240) PRIMARY KEY,
    prefijo_credito VARCHAR(20) NOT NULL,
    siguiente_credito BIGINT NOT NULL CHECK(siguiente_credito>0),
    prefijo_debito VARCHAR(20) NOT NULL,
    siguiente_debito BIGINT NOT NULL CHECK(siguiente_debito>0),
    version BIGINT NOT NULL DEFAULT 0
);
CREATE TABLE IF NOT EXISTS ajustes_fiscales (
    id UUID PRIMARY KEY,
    solicitud_id UUID NOT NULL UNIQUE,
    venta_id UUID NOT NULL REFERENCES ventas(id),
    documento_id UUID NOT NULL UNIQUE REFERENCES documentos_fiscales(id),
    origen_clave VARCHAR(240) NOT NULL,
    origen_hash VARCHAR(64) NOT NULL,
    origen_numero VARCHAR(80) NOT NULL,
    proveedor VARCHAR(20) NOT NULL CHECK(proveedor IN ('DATAICO','DIAN_DIRECT')),
    ambiente VARCHAR(20) NOT NULL CHECK(ambiente IN ('PRUEBAS','PRODUCCION')),
    tipo VARCHAR(20) NOT NULL CHECK(tipo IN ('RETURN','CANCELLATION','INCREASE')),
    numero VARCHAR(80) NOT NULL,
    total NUMERIC(19,2) NOT NULL CHECK(total>0),
    estado VARCHAR(30) NOT NULL CHECK(estado IN ('BORRADOR','EN_CURSO','RECIBIDO','INCIERTO','ERROR_SEGURO','DESCARTADO')),
    snapshot_json TEXT NOT NULL,
    respuesta_json TEXT,
    propietario UUID,
    creado_en TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    actualizado_en TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    version BIGINT NOT NULL DEFAULT 0,
    UNIQUE(origen_clave,numero)
);
CREATE INDEX IF NOT EXISTS idx_ajustes_origen ON ajustes_fiscales(origen_clave,estado);
CREATE INDEX IF NOT EXISTS idx_ajustes_fecha ON ajustes_fiscales(creado_en DESC);
CREATE OR REPLACE FUNCTION proteger_snapshot_ajuste() RETURNS trigger LANGUAGE plpgsql AS $$
BEGIN
    IF TG_OP='DELETE' THEN RAISE EXCEPTION 'No elimine ajustes fiscales; descarte un borrador sin envio.'; END IF;
    IF (NEW.id,NEW.solicitud_id,NEW.venta_id,NEW.documento_id,NEW.origen_clave,NEW.origen_hash,NEW.origen_numero,NEW.proveedor,NEW.ambiente,NEW.tipo,NEW.numero,NEW.total,NEW.snapshot_json,NEW.creado_en)
        IS DISTINCT FROM
       (OLD.id,OLD.solicitud_id,OLD.venta_id,OLD.documento_id,OLD.origen_clave,OLD.origen_hash,OLD.origen_numero,OLD.proveedor,OLD.ambiente,OLD.tipo,OLD.numero,OLD.total,OLD.snapshot_json,OLD.creado_en)
    THEN RAISE EXCEPTION 'El snapshot del ajuste es inmutable.'; END IF;
    RETURN NEW;
END; $$;
DROP TRIGGER IF EXISTS ajustes_snapshot_inmutable ON ajustes_fiscales;
CREATE TRIGGER ajustes_snapshot_inmutable BEFORE UPDATE OR DELETE ON ajustes_fiscales FOR EACH ROW EXECUTE FUNCTION proteger_snapshot_ajuste();

-- V20261119_3__transporte_dian_habilitacion.sql
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

-- V20261119_4__entrega_documentos_fiscales.sql
-- Aditiva: configuracion de entrega y evidencia; no cambia documentos ni numeraciones.
CREATE TABLE IF NOT EXISTS fiscal_entrega_configuracion (
 id BIGINT PRIMARY KEY CHECK(id=1), datos_json TEXT, secreto_cifrado TEXT, version BIGINT NOT NULL DEFAULT 0
);
INSERT INTO fiscal_entrega_configuracion(id) VALUES(1) ON CONFLICT DO NOTHING;
CREATE TABLE IF NOT EXISTS fiscal_correos (
 id UUID PRIMARY KEY, documento_id UUID NOT NULL REFERENCES documentos_fiscales(id), destinatario VARCHAR(254) NOT NULL,
 modo VARCHAR(20) NOT NULL CHECK(modo IN ('SIMULACION','SMTP')), configuracion_version BIGINT NOT NULL,
 estado VARCHAR(30) NOT NULL CHECK(estado IN ('EN_CURSO','SIMULADO','ENVIADO','INCIERTO','RECHAZADO')), mensaje VARCHAR(500) NOT NULL,
 paquete BYTEA NOT NULL, paquete_huella VARCHAR(64) NOT NULL, creado_en TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
 actualizado_en TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP, version BIGINT NOT NULL DEFAULT 0
);
CREATE INDEX IF NOT EXISTS idx_fiscal_correos_documento ON fiscal_correos(documento_id,creado_en DESC);
CREATE OR REPLACE FUNCTION proteger_correo_fiscal() RETURNS trigger LANGUAGE plpgsql AS $$
BEGIN
 IF TG_OP='DELETE' THEN RAISE EXCEPTION 'Conserve la evidencia de entrega fiscal.'; END IF;
 IF (NEW.id,NEW.documento_id,NEW.destinatario,NEW.modo,NEW.configuracion_version,NEW.paquete,NEW.paquete_huella,NEW.creado_en)
 IS DISTINCT FROM (OLD.id,OLD.documento_id,OLD.destinatario,OLD.modo,OLD.configuracion_version,OLD.paquete,OLD.paquete_huella,OLD.creado_en)
 THEN RAISE EXCEPTION 'El destinatario y el paquete de entrega son inmutables.'; END IF;
 RETURN NEW;
END; $$;
DROP TRIGGER IF EXISTS correo_fiscal_inmutable ON fiscal_correos;
CREATE TRIGGER correo_fiscal_inmutable BEFORE UPDATE OR DELETE ON fiscal_correos FOR EACH ROW EXECUTE FUNCTION proteger_correo_fiscal();

COMMIT;
