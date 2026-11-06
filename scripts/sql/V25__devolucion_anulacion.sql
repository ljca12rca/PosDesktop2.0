-- Requiere el esquema hasta V24. Aditivo y repetible; no altera ventas ni credenciales.
BEGIN;
SET LOCAL search_path TO public, pg_catalog;
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
COMMIT;
