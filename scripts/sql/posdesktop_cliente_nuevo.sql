-- INSTALACION NUEVA: PostgreSQL, esquema public VACIO. Ejecutar completo, con API detenida.
-- Edita los tres valores de instalacion_admin antes de ejecutar.
-- En application.yml: spring.flyway.enabled: false (instalacion manual, sin historial Flyway).
-- No ejecutar despues las migraciones historicas: incluyen usuarios y numeracion de otro negocio.
BEGIN;
SET LOCAL lock_timeout = '5s';
SET LOCAL statement_timeout = '120s';
DO $guard$
BEGIN
    IF EXISTS (SELECT 1 FROM pg_tables WHERE schemaname = 'public') THEN
        RAISE EXCEPTION 'La base no esta vacia. Este archivo es solo para una instalacion nueva.';
    END IF;
END
$guard$;
CREATE TEMP TABLE instalacion_admin (usuario TEXT, nombre TEXT, clave TEXT) ON COMMIT DROP;
INSERT INTO instalacion_admin VALUES (
    'admin',
    'Administrador',
    'CAMBIAR_CLAVE_ANTES_DE_EJECUTAR'
);
DO $validate$
DECLARE config RECORD;
BEGIN
    SELECT * INTO config FROM instalacion_admin;
    IF config.clave = 'CAMBIAR_CLAVE_ANTES_DE_EJECUTAR' OR length(config.clave) < 12
        OR octet_length(config.clave) > 72 THEN
        RAISE EXCEPTION 'Define una clave propia: minimo 12 caracteres y maximo 72 bytes UTF-8.';
    END IF;
    IF config.usuario !~ '^[a-z0-9._-]{1,80}$' OR length(trim(config.nombre)) NOT BETWEEN 1 AND 160 THEN
        RAISE EXCEPTION 'Revisa usuario (minusculas, sin espacios) y nombre del administrador.';
    END IF;
END
$validate$;

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
SELECT usuario, nombre, crypt(clave, gen_salt('bf', 10)), TRUE FROM instalacion_admin;
INSERT INTO usuarios_roles (usuario_id, rol_id)
SELECT u.id, r.id FROM usuarios_sistema u
JOIN instalacion_admin c ON c.usuario = u.username
CROSS JOIN roles_sistema r WHERE r.codigo = 'ADMIN';
CREATE OR REPLACE FUNCTION pos_default_responsable_cierre()
RETURNS UUID LANGUAGE SQL STABLE AS $$
    SELECT u.id FROM usuarios_sistema u
    JOIN usuarios_roles ur ON ur.usuario_id = u.id
    JOIN roles_sistema r ON r.id = ur.rol_id
    WHERE u.activo AND r.activo AND r.codigo = 'ADMIN'
    ORDER BY u.creado_en, u.id LIMIT 1
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
    medio_pago VARCHAR(30) NOT NULL DEFAULT 'EFECTIVO'
        CHECK (medio_pago IN ('EFECTIVO', 'TRANSFERENCIA_QR')),
    observacion VARCHAR(500),
    cierre_diario_id UUID REFERENCES cierres_diarios (id),
    creado_en TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    actualizado_en TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE INDEX idx_ventas_fecha_venta ON ventas (fecha_venta);
CREATE INDEX idx_ventas_cierre_diario_id ON ventas (cierre_diario_id);
CREATE INDEX idx_ventas_medio_pago ON ventas (medio_pago);

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
    responsable_usuario_id UUID NOT NULL REFERENCES usuarios_sistema (id) ON DELETE RESTRICT,
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
    responsable_usuario_id UUID NOT NULL REFERENCES usuarios_sistema (id) ON DELETE RESTRICT,
    creado_en TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    actualizado_en TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE INDEX idx_abonos_separado_separado_id ON abonos_separado (separado_id);
CREATE INDEX idx_abonos_separado_fecha_abono ON abonos_separado (fecha_abono);
CREATE INDEX idx_separados_responsable_usuario_id ON separados (responsable_usuario_id);
CREATE INDEX idx_abonos_separado_responsable_usuario_id ON abonos_separado (responsable_usuario_id);

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


-- Conserva la trazabilidad del usuario que abre un separado y de quien registra cada abono.
-- Las filas historicas se asignan al responsable por defecto definido en V9.

ALTER TABLE separados
ADD COLUMN IF NOT EXISTS responsable_usuario_id UUID;

ALTER TABLE abonos_separado
ADD COLUMN IF NOT EXISTS responsable_usuario_id UUID;

UPDATE separados
SET responsable_usuario_id = pos_default_responsable_cierre()
WHERE responsable_usuario_id IS NULL;

UPDATE abonos_separado
SET responsable_usuario_id = pos_default_responsable_cierre()
WHERE responsable_usuario_id IS NULL;

DO $$
BEGIN
    IF EXISTS (SELECT 1 FROM separados WHERE responsable_usuario_id IS NULL)
       OR EXISTS (SELECT 1 FROM abonos_separado WHERE responsable_usuario_id IS NULL) THEN
        RAISE EXCEPTION 'No fue posible asignar un responsable a los separados historicos. Verifica el usuario por defecto.';
    END IF;
END
$$;

ALTER TABLE separados
ALTER COLUMN responsable_usuario_id SET NOT NULL;

ALTER TABLE abonos_separado
ALTER COLUMN responsable_usuario_id SET NOT NULL;

DO $$
BEGIN
    IF NOT EXISTS (
        SELECT 1 FROM pg_constraint WHERE conname = 'fk_separados_responsable_usuario'
    ) THEN
        ALTER TABLE separados
        ADD CONSTRAINT fk_separados_responsable_usuario
        FOREIGN KEY (responsable_usuario_id)
        REFERENCES usuarios_sistema (id)
        ON DELETE RESTRICT;
    END IF;

    IF NOT EXISTS (
        SELECT 1 FROM pg_constraint WHERE conname = 'fk_abonos_separado_responsable_usuario'
    ) THEN
        ALTER TABLE abonos_separado
        ADD CONSTRAINT fk_abonos_separado_responsable_usuario
        FOREIGN KEY (responsable_usuario_id)
        REFERENCES usuarios_sistema (id)
        ON DELETE RESTRICT;
    END IF;
END
$$;

CREATE INDEX IF NOT EXISTS idx_separados_responsable_usuario_id
ON separados (responsable_usuario_id);

CREATE INDEX IF NOT EXISTS idx_abonos_separado_responsable_usuario_id
ON abonos_separado (responsable_usuario_id);


-- Clasifica las ventas por medio de pago para separar efectivo fisico de transferencias QR.

ALTER TABLE ventas
ADD COLUMN IF NOT EXISTS medio_pago VARCHAR(30);

UPDATE ventas
SET medio_pago = 'EFECTIVO'
WHERE medio_pago IS NULL;

ALTER TABLE ventas
ALTER COLUMN medio_pago SET DEFAULT 'EFECTIVO';

ALTER TABLE ventas
ALTER COLUMN medio_pago SET NOT NULL;

DO $$
BEGIN
    IF NOT EXISTS (
        SELECT 1 FROM pg_constraint WHERE conname = 'ck_ventas_medio_pago'
    ) THEN
        ALTER TABLE ventas
        ADD CONSTRAINT ck_ventas_medio_pago
        CHECK (medio_pago IN ('EFECTIVO', 'TRANSFERENCIA_QR'));
    END IF;
END
$$;

CREATE INDEX IF NOT EXISTS idx_ventas_medio_pago
ON ventas (medio_pago);


-- Mantiene la unicidad de NIT informado, pero permite proveedores sin NIT.
-- PostgreSQL permite multiples valores NULL en una restriccion UNIQUE.

ALTER TABLE proveedores
ALTER COLUMN nit DROP NOT NULL;


CREATE TABLE clientes_electronicos (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    tipo_identificacion VARCHAR(20) NOT NULL,
    tipo_persona VARCHAR(10) NOT NULL,
    identificacion VARCHAR(40) NOT NULL,
    digito_verificacion VARCHAR(1),
    razon_social VARCHAR(160) NOT NULL,
    primer_nombre VARCHAR(80),
    segundo_nombre VARCHAR(80),
    primer_apellido VARCHAR(80),
    segundo_apellido VARCHAR(80),
    email VARCHAR(120),
    telefono VARCHAR(40),
    direccion VARCHAR(250),
    ciudad_codigo_dane VARCHAR(10),
    departamento_codigo_dane VARCHAR(10),
    pais_codigo VARCHAR(2) NOT NULL DEFAULT 'CO',
    creado_en TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    actualizado_en TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT chk_clientes_electronicos_tipo_persona CHECK (tipo_persona IN ('JURIDICA', 'NATURAL')),
    CONSTRAINT uk_clientes_electronicos_tipo_identificacion UNIQUE (tipo_identificacion, identificacion)
);

CREATE INDEX idx_clientes_electronicos_razon_social ON clientes_electronicos (razon_social);


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


CREATE TABLE IF NOT EXISTS dataico_consecutivos (
    prefijo VARCHAR(40) PRIMARY KEY,
    siguiente_numero BIGINT NOT NULL CHECK (siguiente_numero > 0)
);



ALTER TABLE facturas_electronicas ADD COLUMN IF NOT EXISTS cliente_email VARCHAR(120);

-- Completa el destinatario en facturas existentes sólo cuando el documento identifica un correo único.
UPDATE facturas_electronicas AS factura
SET cliente_email = cliente.email
FROM (
    SELECT identificacion, MIN(TRIM(email)) AS email
    FROM clientes_electronicos
    WHERE email IS NOT NULL AND TRIM(email) <> ''
    GROUP BY identificacion
    HAVING COUNT(DISTINCT LOWER(TRIM(email))) = 1
) AS cliente
WHERE factura.cliente_email IS NULL AND factura.cliente_identificacion = cliente.identificacion;


-- Los NULL conservan los ajustes del YAML hasta el primer guardado desde el POS.
-- No modifica ni reinicia dataico_consecutivos, ni la numeración local VTA.
CREATE TABLE IF NOT EXISTS dataico_configuracion (
    id INTEGER PRIMARY KEY CHECK (id = 1),
    prefijo VARCHAR(40),
    resolucion VARCHAR(40),
    ambiente VARCHAR(20),
    flexible BOOLEAN,
    version BIGINT NOT NULL DEFAULT 0
);
INSERT INTO dataico_configuracion (id, version) VALUES (1, 0) ON CONFLICT (id) DO NOTHING;


ALTER TABLE dataico_configuracion ADD COLUMN IF NOT EXISTS token_cifrado TEXT;
-- No copia ni sobrescribe el token del YAML: seguirá usándose hasta guardar uno desde el POS.


-- NULL mantiene los valores actuales del YAML hasta guardar desde el POS.
ALTER TABLE dataico_configuracion ADD COLUMN IF NOT EXISTS account_id VARCHAR(36);
ALTER TABLE dataico_configuracion ADD COLUMN IF NOT EXISTS habilitado BOOLEAN;
ALTER TABLE dataico_configuracion ADD COLUMN IF NOT EXISTS base_url VARCHAR(200);
ALTER TABLE dataico_configuracion ADD COLUMN IF NOT EXISTS connect_timeout_seconds INTEGER;
ALTER TABLE dataico_configuracion ADD COLUMN IF NOT EXISTS read_timeout_seconds INTEGER;


-- El nombre admite la misma longitud que la descripción del detalle de venta.
ALTER TABLE articulos ALTER COLUMN nombre TYPE VARCHAR(200);
CREATE INDEX idx_articulos_precio_activos ON articulos (precio_base) WHERE activo = TRUE;
CREATE INDEX idx_articulos_nombre_normalizado
    ON articulos (lower(regexp_replace(trim(nombre), '[[:space:]]+', ' ', 'g')));


-- La conexion electronica comienza deshabilitada, sin credenciales ni consecutivos heredados.
UPDATE dataico_configuracion SET habilitado = FALSE WHERE id = 1;
INSERT INTO clientes_electronicos (
    tipo_identificacion, tipo_persona, identificacion, razon_social,
    primer_nombre, primer_apellido, pais_codigo
) VALUES ('CC', 'NATURAL', '222222222222', 'CONSUMIDOR FINAL', 'CONSUMIDOR', 'FINAL', 'CO');
COMMIT;
-- Revisar: un unico usuario ADMIN; ventas y consecutivos vacios; conexion electronica apagada.
SELECT username, nombre_completo, activo FROM usuarios_sistema;
SELECT habilitado FROM dataico_configuracion WHERE id = 1;