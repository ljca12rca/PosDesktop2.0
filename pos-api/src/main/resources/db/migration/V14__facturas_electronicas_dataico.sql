CREATE TABLE facturas_electronicas (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    venta_id UUID NOT NULL UNIQUE REFERENCES ventas (id),
    fecha_emision TIMESTAMP NOT NULL,
    numeracion VARCHAR(40),
    numero_factura VARCHAR(80) NOT NULL UNIQUE,
    cliente_nombre VARCHAR(200) NOT NULL,
    cliente_identificacion VARCHAR(40) NOT NULL,
    impuesto_total NUMERIC(19, 2) NOT NULL DEFAULT 0,
    retencion_total NUMERIC(19, 2) NOT NULL DEFAULT 0,
    total NUMERIC(19, 2) NOT NULL DEFAULT 0,
    estado_dian VARCHAR(40) NOT NULL,
    estado_cliente VARCHAR(40) NOT NULL,
    estado_correo VARCHAR(40) NOT NULL,
    mensaje_dian VARCHAR(1000),
    mensaje_correo VARCHAR(1000),
    pdf_url VARCHAR(1000),
    identificador_dataico VARCHAR(120),
    creado_en TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    actualizado_en TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE INDEX idx_facturas_electronicas_fecha ON facturas_electronicas (fecha_emision DESC);
CREATE INDEX idx_facturas_electronicas_cliente ON facturas_electronicas (cliente_identificacion);
