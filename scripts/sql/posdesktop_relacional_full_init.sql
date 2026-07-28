-- Inicializacion relacional completa para una base PostgreSQL nueva.
-- Este script consolida el estado final del esquema a partir de V1..V9.
-- Uso recomendado:
--   1. Crear una base vacia.
--   2. Ejecutar este archivo una sola vez.
--   3. Si vas a usar Flyway despues, no mezcles este script manual con las migraciones historicas
--      sobre la misma base sin baselining previo.

CREATE EXTENSION IF NOT EXISTS pgcrypto;

CREATE TABLE permisos_sistema (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    codigo VARCHAR(80) NOT NULL UNIQUE,
    nombre VARCHAR(140) NOT NULL,
    descripcion VARCHAR(300),
    activo BOOLEAN NOT NULL DEFAULT TRUE,
    creado_en TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    actualizado_en TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE TABLE roles_sistema (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    codigo VARCHAR(60) NOT NULL UNIQUE,
    nombre VARCHAR(140) NOT NULL,
    descripcion VARCHAR(300),
    activo BOOLEAN NOT NULL DEFAULT TRUE,
    creado_en TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    actualizado_en TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE TABLE usuarios_sistema (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    username VARCHAR(80) NOT NULL UNIQUE,
    nombre_completo VARCHAR(160) NOT NULL,
    password_hash VARCHAR(120) NOT NULL,
    activo BOOLEAN NOT NULL DEFAULT TRUE,
    ultimo_ingreso TIMESTAMP,
    creado_en TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    actualizado_en TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE TABLE roles_permisos (
    rol_id UUID NOT NULL REFERENCES roles_sistema (id) ON DELETE CASCADE,
    permiso_id UUID NOT NULL REFERENCES permisos_sistema (id) ON DELETE CASCADE,
    PRIMARY KEY (rol_id, permiso_id)
);

CREATE TABLE usuarios_roles (
    usuario_id UUID NOT NULL REFERENCES usuarios_sistema (id) ON DELETE CASCADE,
    rol_id UUID NOT NULL REFERENCES roles_sistema (id) ON DELETE CASCADE,
    PRIMARY KEY (usuario_id, rol_id)
);

CREATE TABLE sesiones_usuario (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    usuario_id UUID NOT NULL REFERENCES usuarios_sistema (id) ON DELETE CASCADE,
    token VARCHAR(180) NOT NULL UNIQUE,
    expira_en TIMESTAMP NOT NULL,
    activa BOOLEAN NOT NULL DEFAULT TRUE,
    ultimo_acceso TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    creado_en TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    actualizado_en TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE INDEX idx_sesiones_usuario_token ON sesiones_usuario (token);
CREATE INDEX idx_sesiones_usuario_usuario_id ON sesiones_usuario (usuario_id);

INSERT INTO permisos_sistema (codigo, nombre, descripcion)
VALUES
    ('VENTAS_VIEW', 'Ver ventas', 'Permite consultar la seccion de ventas.'),
    ('VENTAS_EDIT', 'Registrar ventas', 'Permite registrar ventas manuales.'),
    ('CIERRES_VIEW', 'Ver cierres', 'Permite consultar resumenes e historial de cierres.'),
    ('CIERRES_EDIT', 'Registrar cierres', 'Permite registrar cierres de caja.'),
    ('SEPARADOS_VIEW', 'Ver separados', 'Permite consultar separados y sus abonos.'),
    ('SEPARADOS_EDIT', 'Gestionar separados', 'Permite crear separados y registrar abonos.'),
    ('MOVIMIENTOS_VIEW', 'Ver movimientos', 'Permite consultar movimientos de caja.'),
    ('FACTURAS_VIEW', 'Ver facturas', 'Permite consultar facturas y cartera de proveedores.'),
    ('FACTURAS_EDIT', 'Gestionar facturas', 'Permite crear, editar facturas y registrar abonos.'),
    ('PROVEEDORES_VIEW', 'Ver proveedores', 'Permite consultar proveedores.'),
    ('PROVEEDORES_EDIT', 'Gestionar proveedores', 'Permite crear y actualizar proveedores.');

INSERT INTO roles_sistema (codigo, nombre, descripcion)
VALUES
    ('ADMIN', 'Administrador', 'Acceso total al sistema POS.'),
    ('CAJERO', 'Cajero', 'Opera ventas y separados.'),
    ('COMPRAS', 'Compras', 'Gestiona proveedores y facturas.'),
    ('AUDITOR', 'Auditor', 'Consulta secciones sin permisos de modificacion.'),
    ('OPERACIONES', 'Operaciones', 'Acceso operativo general sin gestion de facturas ni proveedores.');

INSERT INTO roles_permisos (rol_id, permiso_id)
SELECT rol.id, permiso.id
FROM roles_sistema rol
JOIN permisos_sistema permiso ON permiso.codigo IN (
    'VENTAS_VIEW', 'VENTAS_EDIT', 'CIERRES_VIEW', 'CIERRES_EDIT', 'SEPARADOS_VIEW', 'SEPARADOS_EDIT',
    'MOVIMIENTOS_VIEW', 'FACTURAS_VIEW', 'FACTURAS_EDIT', 'PROVEEDORES_VIEW', 'PROVEEDORES_EDIT'
)
WHERE rol.codigo = 'ADMIN';

INSERT INTO roles_permisos (rol_id, permiso_id)
SELECT rol.id, permiso.id
FROM roles_sistema rol
JOIN permisos_sistema permiso ON permiso.codigo IN (
    'VENTAS_VIEW', 'VENTAS_EDIT', 'SEPARADOS_VIEW', 'SEPARADOS_EDIT', 'MOVIMIENTOS_VIEW'
)
WHERE rol.codigo = 'CAJERO';

INSERT INTO roles_permisos (rol_id, permiso_id)
SELECT rol.id, permiso.id
FROM roles_sistema rol
JOIN permisos_sistema permiso ON permiso.codigo IN (
    'FACTURAS_VIEW', 'FACTURAS_EDIT', 'PROVEEDORES_VIEW', 'PROVEEDORES_EDIT', 'MOVIMIENTOS_VIEW'
)
WHERE rol.codigo = 'COMPRAS';

INSERT INTO roles_permisos (rol_id, permiso_id)
SELECT rol.id, permiso.id
FROM roles_sistema rol
JOIN permisos_sistema permiso ON permiso.codigo IN (
    'CIERRES_VIEW', 'SEPARADOS_VIEW', 'MOVIMIENTOS_VIEW', 'FACTURAS_VIEW', 'PROVEEDORES_VIEW'
)
WHERE rol.codigo = 'AUDITOR';

INSERT INTO roles_permisos (rol_id, permiso_id)
SELECT rol.id, permiso.id
FROM roles_sistema rol
JOIN permisos_sistema permiso ON permiso.codigo IN (
    'VENTAS_VIEW', 'VENTAS_EDIT', 'CIERRES_VIEW', 'CIERRES_EDIT',
    'SEPARADOS_VIEW', 'SEPARADOS_EDIT', 'MOVIMIENTOS_VIEW'
)
WHERE rol.codigo = 'OPERACIONES';

INSERT INTO usuarios_sistema (username, nombre_completo, password_hash, activo)
VALUES
    ('keli', 'Keli Monsalve', '$2a$10$2uex40juJfr9TKzY4kc.MeIMmq3seYIpxR7Kn2o.dn77.pUieLfSy', TRUE),
    ('adelaida', 'Adelaida', '$2a$10$ihDmBrKipEHljI8Q5XtvzOhTOl6kBRJPO5x6XP4bT/18iGqC/lkkS', TRUE);

INSERT INTO usuarios_roles (usuario_id, rol_id)
SELECT usuario.id, rol.id
FROM usuarios_sistema usuario
JOIN roles_sistema rol ON rol.codigo = 'ADMIN'
WHERE usuario.username = 'keli';

INSERT INTO usuarios_roles (usuario_id, rol_id)
SELECT usuario.id, rol.id
FROM usuarios_sistema usuario
JOIN roles_sistema rol ON rol.codigo = 'OPERACIONES'
WHERE usuario.username = 'adelaida';

CREATE OR REPLACE FUNCTION pos_default_responsable_cierre()
RETURNS UUID
LANGUAGE SQL
STABLE
AS $$
    SELECT id
    FROM usuarios_sistema
    WHERE username = 'keli'
    LIMIT 1
$$;

CREATE TABLE proveedores (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    nit VARCHAR(30) NOT NULL UNIQUE,
    nombre VARCHAR(160) NOT NULL,
    telefono VARCHAR(40),
    email VARCHAR(120),
    direccion VARCHAR(250),
    observacion VARCHAR(500),
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
    monto_recibido_calculado NUMERIC(19, 2) NOT NULL DEFAULT 0,
    cambio_entregado_calculado NUMERIC(19, 2) NOT NULL DEFAULT 0,
    monto_neto_caja_calculado NUMERIC(19, 2) NOT NULL DEFAULT 0,
    base_caja NUMERIC(19, 2) NOT NULL DEFAULT 0,
    egresos NUMERIC(19, 2) NOT NULL DEFAULT 0,
    trabajadoras NUMERIC(19, 2) NOT NULL DEFAULT 0,
    ahorro NUMERIC(19, 2) NOT NULL DEFAULT 0,
    total_final NUMERIC(19, 2) NOT NULL DEFAULT 0,
    observacion VARCHAR(500),
    responsable_usuario_id UUID NOT NULL DEFAULT pos_default_responsable_cierre()
        REFERENCES usuarios_sistema (id),
    creado_en TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    actualizado_en TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE INDEX idx_cierres_diarios_responsable_usuario_id
ON cierres_diarios (responsable_usuario_id);

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
    monto_recibido NUMERIC(19, 2) NOT NULL DEFAULT 0,
    cambio_entregado NUMERIC(19, 2) NOT NULL DEFAULT 0,
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
