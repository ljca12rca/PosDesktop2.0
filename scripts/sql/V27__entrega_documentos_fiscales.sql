-- Aplicacion manual alternativa a Flyway. Requiere migraciones anteriores.
BEGIN;
SET LOCAL search_path TO public, pg_catalog;
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
