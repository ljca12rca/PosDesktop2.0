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
    ('PROVEEDORES_EDIT', 'Gestionar proveedores', 'Permite crear proveedores.')
ON CONFLICT (codigo) DO NOTHING;

INSERT INTO roles_sistema (codigo, nombre, descripcion)
VALUES
    ('ADMIN', 'Administrador', 'Acceso total al sistema POS.'),
    ('CAJERO', 'Cajero', 'Opera ventas y separados.'),
    ('COMPRAS', 'Compras', 'Gestiona proveedores y facturas.'),
    ('AUDITOR', 'Auditor', 'Consulta secciones sin permisos de modificacion.'),
    ('OPERACIONES', 'Operaciones', 'Acceso operativo general sin gestion de facturas ni proveedores.')
ON CONFLICT (codigo) DO NOTHING;

INSERT INTO roles_permisos (rol_id, permiso_id)
SELECT rol.id, permiso.id
FROM roles_sistema rol
JOIN permisos_sistema permiso ON permiso.codigo IN (
    'VENTAS_VIEW', 'VENTAS_EDIT', 'CIERRES_VIEW', 'CIERRES_EDIT', 'SEPARADOS_VIEW', 'SEPARADOS_EDIT',
    'MOVIMIENTOS_VIEW', 'FACTURAS_VIEW', 'FACTURAS_EDIT', 'PROVEEDORES_VIEW', 'PROVEEDORES_EDIT'
)
WHERE rol.codigo = 'ADMIN'
ON CONFLICT DO NOTHING;

INSERT INTO roles_permisos (rol_id, permiso_id)
SELECT rol.id, permiso.id
FROM roles_sistema rol
JOIN permisos_sistema permiso ON permiso.codigo IN (
    'VENTAS_VIEW', 'VENTAS_EDIT', 'SEPARADOS_VIEW', 'SEPARADOS_EDIT', 'MOVIMIENTOS_VIEW'
)
WHERE rol.codigo = 'CAJERO'
ON CONFLICT DO NOTHING;

INSERT INTO roles_permisos (rol_id, permiso_id)
SELECT rol.id, permiso.id
FROM roles_sistema rol
JOIN permisos_sistema permiso ON permiso.codigo IN (
    'FACTURAS_VIEW', 'FACTURAS_EDIT', 'PROVEEDORES_VIEW', 'PROVEEDORES_EDIT', 'MOVIMIENTOS_VIEW'
)
WHERE rol.codigo = 'COMPRAS'
ON CONFLICT DO NOTHING;

INSERT INTO roles_permisos (rol_id, permiso_id)
SELECT rol.id, permiso.id
FROM roles_sistema rol
JOIN permisos_sistema permiso ON permiso.codigo IN (
    'CIERRES_VIEW', 'SEPARADOS_VIEW', 'MOVIMIENTOS_VIEW', 'FACTURAS_VIEW', 'PROVEEDORES_VIEW'
)
WHERE rol.codigo = 'AUDITOR'
ON CONFLICT DO NOTHING;

INSERT INTO roles_permisos (rol_id, permiso_id)
SELECT rol.id, permiso.id
FROM roles_sistema rol
JOIN permisos_sistema permiso ON permiso.codigo IN (
    'VENTAS_VIEW', 'VENTAS_EDIT',
    'CIERRES_VIEW', 'CIERRES_EDIT',
    'SEPARADOS_VIEW', 'SEPARADOS_EDIT',
    'MOVIMIENTOS_VIEW'
)
WHERE rol.codigo = 'OPERACIONES'
ON CONFLICT DO NOTHING;

INSERT INTO usuarios_sistema (username, nombre_completo, password_hash, activo)
VALUES
    ('keli', 'Keli Monsalve', '$2a$10$2uex40juJfr9TKzY4kc.MeIMmq3seYIpxR7Kn2o.dn77.pUieLfSy', TRUE),
    ('adelaida', 'Adelaida', '$2a$10$ihDmBrKipEHljI8Q5XtvzOhTOl6kBRJPO5x6XP4bT/18iGqC/lkkS', TRUE)
ON CONFLICT (username) DO NOTHING;

INSERT INTO usuarios_roles (usuario_id, rol_id)
SELECT usuario.id, rol.id
FROM usuarios_sistema usuario
JOIN roles_sistema rol ON rol.codigo = 'ADMIN'
WHERE usuario.username = 'keli'
ON CONFLICT DO NOTHING;

INSERT INTO usuarios_roles (usuario_id, rol_id)
SELECT usuario.id, rol.id
FROM usuarios_sistema usuario
JOIN roles_sistema rol ON rol.codigo = 'OPERACIONES'
WHERE usuario.username = 'adelaida'
ON CONFLICT DO NOTHING;

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

ALTER TABLE cierres_diarios
ADD COLUMN responsable_usuario_id UUID;

UPDATE cierres_diarios
SET responsable_usuario_id = pos_default_responsable_cierre()
WHERE responsable_usuario_id IS NULL;

ALTER TABLE cierres_diarios
ALTER COLUMN responsable_usuario_id SET DEFAULT pos_default_responsable_cierre(),
ALTER COLUMN responsable_usuario_id SET NOT NULL;

ALTER TABLE cierres_diarios
ADD CONSTRAINT fk_cierres_diarios_responsable_usuario
FOREIGN KEY (responsable_usuario_id) REFERENCES usuarios_sistema (id);

CREATE INDEX idx_cierres_diarios_responsable_usuario_id
ON cierres_diarios (responsable_usuario_id);
