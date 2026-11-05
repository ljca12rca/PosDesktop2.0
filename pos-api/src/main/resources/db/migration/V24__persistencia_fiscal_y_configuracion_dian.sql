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
