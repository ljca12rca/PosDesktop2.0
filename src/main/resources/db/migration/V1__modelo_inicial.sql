CREATE EXTENSION IF NOT EXISTS pgcrypto;

CREATE TABLE proveedores (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    nit VARCHAR(30) NOT NULL UNIQUE,
    nombre VARCHAR(160) NOT NULL,
    telefono VARCHAR(40),
    email VARCHAR(120),
    direccion VARCHAR(250),
    activo BOOLEAN NOT NULL DEFAULT TRUE,
    creado_en TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    actualizado_en TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE TABLE articulos (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    sku VARCHAR(50) NOT NULL UNIQUE,
    nombre VARCHAR(150) NOT NULL,
    descripcion VARCHAR(500),
    precio_base NUMERIC(19, 2) NOT NULL DEFAULT 0,
    activo BOOLEAN NOT NULL DEFAULT TRUE,
    creado_en TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    actualizado_en TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE TABLE cierres_diarios (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    fecha_operacion DATE NOT NULL UNIQUE,
    fecha_hora_cierre TIMESTAMP NOT NULL,
    estado VARCHAR(20) NOT NULL,
    cantidad_ventas INTEGER NOT NULL DEFAULT 0,
    subtotal_calculado NUMERIC(19, 2) NOT NULL DEFAULT 0,
    descuento_calculado NUMERIC(19, 2) NOT NULL DEFAULT 0,
    impuesto_calculado NUMERIC(19, 2) NOT NULL DEFAULT 0,
    total_calculado NUMERIC(19, 2) NOT NULL DEFAULT 0,
    observacion VARCHAR(500),
    creado_en TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    actualizado_en TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE TABLE ventas (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    numero_venta VARCHAR(40) NOT NULL UNIQUE,
    fecha_venta TIMESTAMP NOT NULL,
    estado VARCHAR(20) NOT NULL,
    origen VARCHAR(20) NOT NULL,
    monto_manual_informado NUMERIC(19, 2) NOT NULL DEFAULT 0,
    subtotal NUMERIC(19, 2) NOT NULL DEFAULT 0,
    descuento_total NUMERIC(19, 2) NOT NULL DEFAULT 0,
    impuesto_total NUMERIC(19, 2) NOT NULL DEFAULT 0,
    total NUMERIC(19, 2) NOT NULL DEFAULT 0,
    observacion VARCHAR(500),
    cierre_diario_id UUID REFERENCES cierres_diarios (id),
    creado_en TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    actualizado_en TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE INDEX idx_ventas_fecha_venta ON ventas (fecha_venta);
CREATE INDEX idx_ventas_cierre_diario_id ON ventas (cierre_diario_id);

CREATE TABLE detalle_venta (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    venta_id UUID NOT NULL REFERENCES ventas (id) ON DELETE CASCADE,
    articulo_id UUID REFERENCES articulos (id),
    tipo_detalle VARCHAR(20) NOT NULL,
    orden INTEGER NOT NULL,
    descripcion VARCHAR(200) NOT NULL,
    cantidad NUMERIC(19, 3) NOT NULL DEFAULT 1,
    unidad_medida VARCHAR(20) NOT NULL DEFAULT 'UND',
    precio_unitario NUMERIC(19, 2) NOT NULL DEFAULT 0,
    subtotal NUMERIC(19, 2) NOT NULL DEFAULT 0,
    descuento NUMERIC(19, 2) NOT NULL DEFAULT 0,
    impuesto NUMERIC(19, 2) NOT NULL DEFAULT 0,
    total NUMERIC(19, 2) NOT NULL DEFAULT 0,
    referencia_externa VARCHAR(100),
    creado_en TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    actualizado_en TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE INDEX idx_detalle_venta_venta_id ON detalle_venta (venta_id);
CREATE INDEX idx_detalle_venta_articulo_id ON detalle_venta (articulo_id);

CREATE TABLE facturas_proveedor (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    proveedor_id UUID NOT NULL REFERENCES proveedores (id),
    numero_factura VARCHAR(60) NOT NULL,
    fecha_emision DATE NOT NULL,
    fecha_vencimiento DATE,
    estado VARCHAR(30) NOT NULL,
    monto_total NUMERIC(19, 2) NOT NULL DEFAULT 0,
    monto_pagado NUMERIC(19, 2) NOT NULL DEFAULT 0,
    saldo_pendiente NUMERIC(19, 2) NOT NULL DEFAULT 0,
    observacion VARCHAR(500),
    creado_en TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    actualizado_en TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT uk_facturas_proveedor_numero UNIQUE (proveedor_id, numero_factura)
);

CREATE INDEX idx_facturas_proveedor_proveedor_id ON facturas_proveedor (proveedor_id);
CREATE INDEX idx_facturas_proveedor_estado ON facturas_proveedor (estado);

CREATE TABLE pagos_factura (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    factura_proveedor_id UUID NOT NULL REFERENCES facturas_proveedor (id) ON DELETE CASCADE,
    fecha_pago DATE NOT NULL,
    monto_pago NUMERIC(19, 2) NOT NULL DEFAULT 0,
    metodo_pago VARCHAR(30) NOT NULL,
    referencia_pago VARCHAR(100),
    observacion VARCHAR(500),
    creado_en TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    actualizado_en TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE INDEX idx_pagos_factura_factura_id ON pagos_factura (factura_proveedor_id);
