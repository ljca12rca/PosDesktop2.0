CREATE TABLE separados (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    numero_separado VARCHAR(40) NOT NULL UNIQUE,
    fecha_separacion DATE NOT NULL,
    estado VARCHAR(20) NOT NULL,
    nombre_cliente VARCHAR(160) NOT NULL,
    documento_cliente VARCHAR(40),
    telefono_cliente VARCHAR(40),
    articulo_id UUID REFERENCES articulos (id),
    descripcion_articulo VARCHAR(200) NOT NULL,
    cantidad NUMERIC(19, 3) NOT NULL DEFAULT 1,
    valor_total NUMERIC(19, 2) NOT NULL DEFAULT 0,
    monto_minimo_inicial NUMERIC(19, 2) NOT NULL DEFAULT 20000,
    total_abonado NUMERIC(19, 2) NOT NULL DEFAULT 0,
    saldo_pendiente NUMERIC(19, 2) NOT NULL DEFAULT 0,
    fecha_promesa_entrega DATE,
    fecha_entrega DATE,
    observacion VARCHAR(500),
    creado_en TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    actualizado_en TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE INDEX idx_separados_estado ON separados (estado);
CREATE INDEX idx_separados_articulo_id ON separados (articulo_id);
CREATE INDEX idx_separados_fecha_separacion ON separados (fecha_separacion);

CREATE TABLE abonos_separado (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    separado_id UUID NOT NULL REFERENCES separados (id) ON DELETE CASCADE,
    venta_id UUID NOT NULL UNIQUE REFERENCES ventas (id),
    numero_abono INTEGER NOT NULL,
    fecha_abono TIMESTAMP NOT NULL,
    monto_abono NUMERIC(19, 2) NOT NULL DEFAULT 0,
    abono_inicial BOOLEAN NOT NULL DEFAULT FALSE,
    observacion VARCHAR(500),
    creado_en TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    actualizado_en TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE INDEX idx_abonos_separado_separado_id ON abonos_separado (separado_id);
CREATE INDEX idx_abonos_separado_fecha_abono ON abonos_separado (fecha_abono);
