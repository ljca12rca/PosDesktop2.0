-- Ejecutar completo en posdesktop, con un usuario propietario de las tablas.
-- Si la conexion tiene una transaccion fallida, ejecutar ROLLBACK por separado antes.
-- Solo agrega consulta. Conserva claves, roles y permisos operativos previos.
-- No otorga ADMIN, edicion adicional ni acceso a Configuracion Dataico.
BEGIN;
SET LOCAL search_path TO public, pg_catalog;
SET LOCAL lock_timeout TO '5s';
SET LOCAL statement_timeout TO '120s';
SELECT pg_advisory_xact_lock(hashtextextended('posdesktop_consulta_modulos_usuarios', 0));

DO $consulta$
DECLARE
    consulta_rol_id UUID;
BEGIN
    IF (SELECT count(*) FROM usuarios_sistema
        WHERE lower(username) IN ('willinton', 'usuario') AND activo) <> 2 THEN
        RAISE EXCEPTION 'Deben existir exactamente los usuarios willinton y usuario activos. No se modifico su estado.';
    END IF;

    INSERT INTO permisos_sistema (id, codigo, nombre, descripcion, activo, creado_en, actualizado_en)
    SELECT gen_random_uuid(), p.codigo, p.nombre, 'Consulta de modulo POS.', TRUE,
        CURRENT_TIMESTAMP, CURRENT_TIMESTAMP
    FROM (VALUES
        ('VENTAS_VIEW', 'Ver ventas'), ('CIERRES_VIEW', 'Ver cierres'),
        ('SEPARADOS_VIEW', 'Ver separados'), ('MOVIMIENTOS_VIEW', 'Ver movimientos'),
        ('FACTURAS_VIEW', 'Ver facturas'), ('PROVEEDORES_VIEW', 'Ver proveedores')
    ) AS p(codigo, nombre)
    WHERE NOT EXISTS (SELECT 1 FROM permisos_sistema existente WHERE existente.codigo = p.codigo)
    ON CONFLICT DO NOTHING;

    IF (SELECT count(*) FROM permisos_sistema WHERE activo AND codigo IN (
        'VENTAS_VIEW','CIERRES_VIEW','SEPARADOS_VIEW','MOVIMIENTOS_VIEW','FACTURAS_VIEW','PROVEEDORES_VIEW')) <> 6 THEN
        RAISE EXCEPTION 'Hay permisos de consulta desactivados o duplicados. Revisar antes de habilitarlos.';
    END IF;

    INSERT INTO roles_sistema (id, codigo, nombre, descripcion, activo, creado_en, actualizado_en)
    SELECT gen_random_uuid(), 'CONSULTA_MODULOS_INICIALES', 'Consulta de modulos',
        'Consulta adicional de modulos para willinton y usuario.', TRUE, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP
    WHERE NOT EXISTS (SELECT 1 FROM roles_sistema WHERE codigo='CONSULTA_MODULOS_INICIALES')
    ON CONFLICT DO NOTHING;
    SELECT id INTO consulta_rol_id FROM roles_sistema WHERE codigo='CONSULTA_MODULOS_INICIALES' AND activo;
    IF consulta_rol_id IS NULL THEN
        RAISE EXCEPTION 'El rol de consulta existe pero esta desactivado. No se reactiva automaticamente.';
    END IF;
    IF EXISTS (SELECT 1 FROM usuarios_roles ur JOIN usuarios_sistema u ON u.id=ur.usuario_id
        WHERE ur.rol_id=consulta_rol_id AND lower(u.username) NOT IN ('willinton','usuario')) THEN
        RAISE EXCEPTION 'El rol de consulta ya pertenece a otros usuarios. Se cancela para no modificar sus accesos.';
    END IF;

    INSERT INTO roles_permisos (rol_id, permiso_id)
    SELECT consulta_rol_id, p.id FROM permisos_sistema p WHERE p.codigo IN (
        'VENTAS_VIEW','CIERRES_VIEW','SEPARADOS_VIEW','MOVIMIENTOS_VIEW','FACTURAS_VIEW','PROVEEDORES_VIEW')
    AND NOT EXISTS (SELECT 1 FROM roles_permisos rp WHERE rp.rol_id=consulta_rol_id AND rp.permiso_id=p.id)
    ON CONFLICT DO NOTHING;

    INSERT INTO usuarios_roles (usuario_id, rol_id)
    SELECT u.id, consulta_rol_id FROM usuarios_sistema u WHERE lower(u.username) IN ('willinton','usuario')
    AND NOT EXISTS (SELECT 1 FROM usuarios_roles ur WHERE ur.usuario_id=u.id AND ur.rol_id=consulta_rol_id)
    ON CONFLICT DO NOTHING;
END
$consulta$;
COMMIT;

-- Comprobacion de los permisos efectivos; no muestra contrasenas.
SELECT u.username, string_agg(DISTINCT p.codigo, ', ' ORDER BY p.codigo) AS permisos
FROM usuarios_sistema u
JOIN usuarios_roles ur ON ur.usuario_id=u.id
JOIN roles_sistema r ON r.id=ur.rol_id AND r.activo
JOIN roles_permisos rp ON rp.rol_id=r.id
JOIN permisos_sistema p ON p.id=rp.permiso_id AND p.activo
WHERE lower(u.username) IN ('willinton','usuario')
GROUP BY u.username ORDER BY u.username;
