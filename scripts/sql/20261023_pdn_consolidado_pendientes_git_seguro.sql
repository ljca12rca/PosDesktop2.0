-- POS Desktop / PostgreSQL 13+ / esquema public.
-- Consolida los pendientes de Git V12..V23 y revisa los dos correctivos manuales.
-- ADAPTACION PARA PDN: NO es una concatenacion de los originales de pruebas.
-- Requiere la instalacion anterior V1..V11, respaldo verificado y API DETENIDA.
-- Ejecutar el archivo completo: psql -v ON_ERROR_STOP=1 -U pos -d posdesktop -f este_archivo.sql
-- No ejecutar despues los originales V12..V23 mediante Flyway: ver README_PDN_CONSOLIDADO.md.
-- No UPDATE / DELETE / TRUNCATE de datos, ni reajustes de consecutivos o cierres.
-- Si encuentra una incompatibilidad, ABORTA y revierte toda esta transaccion.

BEGIN;
SET LOCAL search_path TO public, pg_catalog;
SET LOCAL lock_timeout TO '5s';
SET LOCAL statement_timeout TO '120s';
SELECT pg_advisory_xact_lock(hashtextextended('posdesktop_pdn_consolidado_20261023', 0));

DO $preflight$
DECLARE
    item RECORD;
BEGIN
    IF current_setting('server_version_num')::INTEGER < 130000 THEN
        RAISE EXCEPTION 'Requiere PostgreSQL 13 o superior. No se ha modificado ningun dato.';
    END IF;
    FOR item IN SELECT * FROM (VALUES
        ('proveedores', 'nit'), ('ventas', 'id'), ('ventas', 'medio_pago'),
        ('cierres_diarios', 'total_final'), ('separados', 'responsable_usuario_id'),
        ('abonos_separado', 'responsable_usuario_id')
    ) AS base(tabla, columna) LOOP
        IF NOT EXISTS (SELECT 1 FROM information_schema.columns
            WHERE table_schema = 'public' AND table_name = item.tabla AND column_name = item.columna) THEN
            RAISE EXCEPTION 'Falta public.%.%. Este consolidado actualiza V12..V23, no reemplaza la instalacion V1..V11. No continue con la API.',
                item.tabla, item.columna;
        END IF;
    END LOOP;
    IF NOT EXISTS (SELECT 1 FROM pg_attribute
        WHERE attrelid = 'public.ventas'::regclass AND attname = 'id' AND atttypid = 'uuid'::regtype) THEN
        RAISE EXCEPTION 'ventas.id no es UUID. Se requiere revision manual; no se convertiran datos.';
    END IF;
END
$preflight$;

-- Funciones temporales: desaparecen al cerrar esta conexion, no se instalan en public.
-- Si falta una columna obligatoria en una tabla con filas, no se inventan valores.
CREATE OR REPLACE FUNCTION pg_temp.pdn_columna(
    tabla TEXT, columna TEXT, definicion TEXT, tipo REGTYPE,
    obligatoria BOOLEAN DEFAULT FALSE, longitud_minima INTEGER DEFAULT NULL,
    defecto_neutro BOOLEAN DEFAULT FALSE
) RETURNS VOID LANGUAGE plpgsql AS $func$
DECLARE
    relacion REGCLASS := to_regclass(format('public.%I', tabla));
    atributo RECORD;
    hay_filas BOOLEAN;
    hay_nulos BOOLEAN;
BEGIN
    SELECT atttypid, atttypmod, attnotnull INTO atributo FROM pg_attribute
        WHERE attrelid = relacion AND attname = columna AND attnum > 0 AND NOT attisdropped;
    IF NOT FOUND THEN
        EXECUTE format('SELECT EXISTS (SELECT 1 FROM public.%I)', tabla) INTO hay_filas;
        IF obligatoria AND hay_filas AND NOT defecto_neutro THEN
            RAISE EXCEPTION 'Falta la columna obligatoria %.% en una tabla con datos. No se rellenara automaticamente; revise la estructura antes de continuar.', tabla, columna;
        END IF;
        EXECUTE format('ALTER TABLE public.%I ADD COLUMN %I %s', tabla, columna, definicion);
        RETURN;
    END IF;
    IF atributo.atttypid <> tipo AND NOT (tipo = 'varchar'::regtype AND atributo.atttypid = 'text'::regtype) THEN
        RAISE EXCEPTION 'Tipo incompatible en %.%: actual %, esperado %. No se cambiara ni convertira la columna.',
            tabla, columna, format_type(atributo.atttypid, atributo.atttypmod), tipo;
    END IF;
    IF longitud_minima IS NOT NULL AND atributo.atttypid = 'varchar'::regtype
        AND atributo.atttypmod <> -1 AND atributo.atttypmod - 4 < longitud_minima THEN
        RAISE EXCEPTION 'Longitud insuficiente en %.%: %, requerida al menos %. Se requiere revision manual.',
            tabla, columna, atributo.atttypmod - 4, longitud_minima;
    END IF;
    IF obligatoria AND NOT atributo.attnotnull THEN
        EXECUTE format('SELECT EXISTS (SELECT 1 FROM public.%I WHERE %I IS NULL)', tabla, columna) INTO hay_nulos;
        IF hay_nulos THEN
            RAISE EXCEPTION '%.% contiene NULL y requiere NOT NULL. No se alteraran registros para corregirlo.', tabla, columna;
        END IF;
        EXECUTE format('ALTER TABLE public.%I ALTER COLUMN %I SET NOT NULL', tabla, columna);
    END IF;
END
$func$;

CREATE OR REPLACE FUNCTION pg_temp.pdn_clave(tabla TEXT, columnas TEXT[], nombre TEXT, primaria BOOLEAN DEFAULT FALSE)
RETURNS VOID LANGUAGE plpgsql AS $func$
DECLARE
    relacion REGCLASS := to_regclass(format('public.%I', tabla));
    numeros SMALLINT[];
    lista TEXT;
BEGIN
    SELECT array_agg(a.attnum ORDER BY x.orden), string_agg(format('%I', x.columna), ', ' ORDER BY x.orden)
        INTO numeros, lista
        FROM unnest(columnas) WITH ORDINALITY AS x(columna, orden)
        JOIN pg_attribute a ON a.attrelid = relacion AND a.attname = x.columna AND NOT a.attisdropped;
    IF EXISTS (SELECT 1 FROM pg_constraint c WHERE c.conrelid = relacion
        AND (c.contype = 'p' OR (NOT primaria AND c.contype = 'u'))
        AND c.conkey @> numeros AND c.conkey <@ numeros) THEN RETURN; END IF;
    IF primaria AND EXISTS (SELECT 1 FROM pg_constraint WHERE conrelid = relacion AND contype = 'p') THEN
        RAISE EXCEPTION 'La clave primaria de % no corresponde a %. No se reemplazara la clave existente.', tabla, lista;
    END IF;
    -- PostgreSQL comprueba duplicados y aborta; NO se eliminan duplicados para crear la clave.
    EXECUTE format('ALTER TABLE public.%I ADD CONSTRAINT %I %s (%s)', tabla, nombre,
        CASE WHEN primaria THEN 'PRIMARY KEY' ELSE 'UNIQUE' END, lista);
END
$func$;

-- V12: solo relaja NOT NULL de NIT; conserva valores, indices y unicidad existentes.
ALTER TABLE public.proveedores ALTER COLUMN nit DROP NOT NULL;

-- V13: clientes electronicos. No se eliminan ni se fusionan clientes existentes.
CREATE TABLE IF NOT EXISTS public.clientes_electronicos ();
SELECT pg_temp.pdn_columna('clientes_electronicos', columna, definicion, tipo::regtype, obligatoria, longitud)
FROM (VALUES
    ('id', 'UUID NOT NULL DEFAULT gen_random_uuid()', 'uuid', TRUE, NULL::INTEGER),
    ('tipo_identificacion', 'VARCHAR(20) NOT NULL', 'varchar', TRUE, 20),
    ('tipo_persona', 'VARCHAR(10) NOT NULL', 'varchar', TRUE, 10),
    ('identificacion', 'VARCHAR(40) NOT NULL', 'varchar', TRUE, 40),
    ('digito_verificacion', 'VARCHAR(1)', 'varchar', FALSE, 1),
    ('razon_social', 'VARCHAR(160) NOT NULL', 'varchar', TRUE, 160),
    ('primer_nombre', 'VARCHAR(80)', 'varchar', FALSE, 80),
    ('segundo_nombre', 'VARCHAR(80)', 'varchar', FALSE, 80),
    ('primer_apellido', 'VARCHAR(80)', 'varchar', FALSE, 80),
    ('segundo_apellido', 'VARCHAR(80)', 'varchar', FALSE, 80),
    ('email', 'VARCHAR(120)', 'varchar', FALSE, 120),
    ('telefono', 'VARCHAR(40)', 'varchar', FALSE, 40),
    ('direccion', 'VARCHAR(250)', 'varchar', FALSE, 250),
    ('ciudad_codigo_dane', 'VARCHAR(10)', 'varchar', FALSE, 10),
    ('departamento_codigo_dane', 'VARCHAR(10)', 'varchar', FALSE, 10),
    ('pais_codigo', 'VARCHAR(2) NOT NULL DEFAULT ''CO''', 'varchar', TRUE, 2),
    ('creado_en', 'TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP', 'timestamp', TRUE, NULL),
    ('actualizado_en', 'TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP', 'timestamp', TRUE, NULL)
) AS columnas(columna, definicion, tipo, obligatoria, longitud);
SELECT pg_temp.pdn_clave('clientes_electronicos', ARRAY['id'], 'clientes_electronicos_pkey', TRUE);
SELECT pg_temp.pdn_clave('clientes_electronicos', ARRAY['tipo_identificacion','identificacion'], 'uk_clientes_electronicos_tipo_identificacion');
DO $check_cliente$
BEGIN
    IF NOT EXISTS (SELECT 1 FROM pg_constraint WHERE conrelid = 'public.clientes_electronicos'::regclass
        AND conname = 'chk_clientes_electronicos_tipo_persona') THEN
        ALTER TABLE public.clientes_electronicos ADD CONSTRAINT chk_clientes_electronicos_tipo_persona
            CHECK (tipo_persona IN ('JURIDICA', 'NATURAL'));
    END IF;
END
$check_cliente$;
CREATE INDEX IF NOT EXISTS idx_clientes_electronicos_razon_social ON public.clientes_electronicos (razon_social);

-- V14 + estructura V20: bitacora electronica ligada a su venta local.
CREATE TABLE IF NOT EXISTS public.facturas_electronicas ();
SELECT pg_temp.pdn_columna('facturas_electronicas', columna, definicion, tipo::regtype, obligatoria, longitud)
FROM (VALUES
    ('id', 'UUID NOT NULL DEFAULT gen_random_uuid()', 'uuid', TRUE, NULL::INTEGER),
    ('venta_id', 'UUID NOT NULL', 'uuid', TRUE, NULL),
    ('fecha_emision', 'TIMESTAMP NOT NULL', 'timestamp', TRUE, NULL),
    ('numeracion', 'VARCHAR(40)', 'varchar', FALSE, 40),
    ('numero_factura', 'VARCHAR(80) NOT NULL', 'varchar', TRUE, 80),
    ('cliente_nombre', 'VARCHAR(200) NOT NULL', 'varchar', TRUE, 200),
    ('cliente_identificacion', 'VARCHAR(40) NOT NULL', 'varchar', TRUE, 40),
    ('cliente_email', 'VARCHAR(120)', 'varchar', FALSE, 120),
    ('impuesto_total', 'NUMERIC(19,2) NOT NULL DEFAULT 0', 'numeric', TRUE, NULL),
    ('retencion_total', 'NUMERIC(19,2) NOT NULL DEFAULT 0', 'numeric', TRUE, NULL),
    ('total', 'NUMERIC(19,2) NOT NULL DEFAULT 0', 'numeric', TRUE, NULL),
    ('estado_dian', 'VARCHAR(40) NOT NULL', 'varchar', TRUE, 40),
    ('estado_cliente', 'VARCHAR(40) NOT NULL', 'varchar', TRUE, 40),
    ('estado_correo', 'VARCHAR(40) NOT NULL', 'varchar', TRUE, 40),
    ('mensaje_dian', 'VARCHAR(1000)', 'varchar', FALSE, 1000),
    ('mensaje_correo', 'VARCHAR(1000)', 'varchar', FALSE, 1000),
    ('pdf_url', 'VARCHAR(1000)', 'varchar', FALSE, 1000),
    ('identificador_dataico', 'VARCHAR(120)', 'varchar', FALSE, 120),
    ('creado_en', 'TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP', 'timestamp', TRUE, NULL),
    ('actualizado_en', 'TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP', 'timestamp', TRUE, NULL)
) AS columnas(columna, definicion, tipo, obligatoria, longitud);
SELECT pg_temp.pdn_clave('facturas_electronicas', ARRAY['id'], 'facturas_electronicas_pkey', TRUE);
SELECT pg_temp.pdn_clave('facturas_electronicas', ARRAY['venta_id'], 'facturas_electronicas_venta_id_key');
SELECT pg_temp.pdn_clave('facturas_electronicas', ARRAY['numero_factura'], 'facturas_electronicas_numero_factura_key');
DO $fk_venta$
DECLARE
    origen SMALLINT := (SELECT attnum FROM pg_attribute WHERE attrelid = 'public.facturas_electronicas'::regclass AND attname = 'venta_id');
    destino SMALLINT := (SELECT attnum FROM pg_attribute WHERE attrelid = 'public.ventas'::regclass AND attname = 'id');
BEGIN
    IF NOT EXISTS (SELECT 1 FROM pg_constraint WHERE conrelid = 'public.facturas_electronicas'::regclass
        AND contype = 'f' AND confrelid = 'public.ventas'::regclass
        AND conkey = ARRAY[origen] AND confkey = ARRAY[destino]) THEN
        ALTER TABLE public.facturas_electronicas ADD CONSTRAINT fk_pdn_factura_electronica_venta
            FOREIGN KEY (venta_id) REFERENCES public.ventas(id);
    END IF;
END
$fk_venta$;
CREATE INDEX IF NOT EXISTS idx_facturas_electronicas_fecha ON public.facturas_electronicas (fecha_emision DESC);
CREATE INDEX IF NOT EXISTS idx_facturas_electronicas_cliente ON public.facturas_electronicas (cliente_identificacion);

-- V15..V18: SOLO estructura. No se copian FEG91861, FEE999999999,
-- 994500001, 1789500095, ni ninguno de los consecutivos del equipo de desarrollo.
CREATE TABLE IF NOT EXISTS public.dataico_consecutivos ();
SELECT pg_temp.pdn_columna('dataico_consecutivos', 'prefijo', 'VARCHAR(40) NOT NULL', 'varchar', TRUE, 40);
SELECT pg_temp.pdn_columna('dataico_consecutivos', 'siguiente_numero', 'BIGINT NOT NULL', 'int8', TRUE);
SELECT pg_temp.pdn_clave('dataico_consecutivos', ARRAY['prefijo'], 'dataico_consecutivos_pkey', TRUE);
DO $check_consecutivo$
BEGIN
    IF NOT EXISTS (SELECT 1 FROM pg_constraint WHERE conrelid = 'public.dataico_consecutivos'::regclass
        AND conname = 'dataico_consecutivos_siguiente_numero_check') THEN
        ALTER TABLE public.dataico_consecutivos ADD CONSTRAINT dataico_consecutivos_siguiente_numero_check CHECK (siguiente_numero > 0);
    END IF;
END
$check_consecutivo$;

-- V21 + V22 + V23. NULL en campos nuevos conserva la configuracion existente.
CREATE TABLE IF NOT EXISTS public.dataico_configuracion ();
SELECT pg_temp.pdn_columna('dataico_configuracion', columna, definicion, tipo::regtype, obligatoria, longitud, neutro)
FROM (VALUES
    ('id', 'INTEGER NOT NULL', 'int4', TRUE, NULL::INTEGER, FALSE),
    ('prefijo', 'VARCHAR(40)', 'varchar', FALSE, 40, FALSE),
    ('resolucion', 'VARCHAR(40)', 'varchar', FALSE, 40, FALSE),
    ('ambiente', 'VARCHAR(20)', 'varchar', FALSE, 20, FALSE),
    ('flexible', 'BOOLEAN', 'bool', FALSE, NULL, FALSE),
    ('version', 'BIGINT NOT NULL DEFAULT 0', 'int8', TRUE, NULL, TRUE),
    ('token_cifrado', 'TEXT', 'text', FALSE, NULL, FALSE),
    ('account_id', 'VARCHAR(36)', 'varchar', FALSE, 36, FALSE),
    ('habilitado', 'BOOLEAN', 'bool', FALSE, NULL, FALSE),
    ('base_url', 'VARCHAR(200)', 'varchar', FALSE, 200, FALSE),
    ('connect_timeout_seconds', 'INTEGER', 'int4', FALSE, NULL, FALSE),
    ('read_timeout_seconds', 'INTEGER', 'int4', FALSE, NULL, FALSE)
) AS columnas(columna, definicion, tipo, obligatoria, longitud, neutro);
SELECT pg_temp.pdn_clave('dataico_configuracion', ARRAY['id'], 'dataico_configuracion_pkey', TRUE);
DO $check_configuracion$
BEGIN
    IF NOT EXISTS (SELECT 1 FROM pg_constraint WHERE conrelid = 'public.dataico_configuracion'::regclass
        AND conname = 'dataico_configuracion_id_check') THEN
        ALTER TABLE public.dataico_configuracion ADD CONSTRAINT dataico_configuracion_id_check CHECK (id = 1);
    END IF;
END
$check_configuracion$;
-- Solo si no existia: inicia deshabilitado, sin token, cuenta ni datos fiscales de pruebas.
INSERT INTO public.dataico_configuracion (id, version, habilitado)
VALUES (1, 0, FALSE) ON CONFLICT (id) DO NOTHING;

-- V19 adaptada: crea el consumidor final solo si falta, sin inventar direccion de PDN.
-- Completar residencia fiscal desde el POS ANTES de emitir para este cliente.
-- Si ya existe, conserva absolutamente todos sus campos.
INSERT INTO public.clientes_electronicos (
    id, tipo_identificacion, tipo_persona, identificacion, razon_social,
    primer_nombre, primer_apellido, pais_codigo, creado_en, actualizado_en
) VALUES (
    gen_random_uuid(), 'CC', 'NATURAL', '222222222222', 'CONSUMIDOR FINAL',
    'CONSUMIDOR', 'FINAL', 'CO', CURRENT_TIMESTAMP, CURRENT_TIMESTAMP
) ON CONFLICT (tipo_identificacion, identificacion) DO NOTHING;

-- Deliberadamente omitidos:
-- * UPDATE de correos historicos de V20.
-- * UPDATE/INSERT de numeros de pruebas de V15..V18 y 20261016_alinear_consecutivo_dataico_fee.sql.
-- * UPDATE de ventas/cierres de 20260813_recalcular_cierres_incluyendo_transferencias.sql.
-- * Escrituras a flyway_schema_history, usuarios existentes, permisos existentes o ventas.

-- Usuarios iniciales solicitados el 2026-10-28. Rol minimo CAJERO, no ADMIN.
-- Clave inicial compartida solo para instalacion: cambiar antes de entregar el equipo.
-- Si un nombre ya existe (incluso con mayusculas), NO modifica clave, estado ni roles.
CREATE EXTENSION IF NOT EXISTS pgcrypto WITH SCHEMA public;
DO $usuarios_iniciales$
DECLARE
    nombre_usuario TEXT;
    nuevo_id UUID;
    rol_cajero UUID;
BEGIN
    -- Algunas instalaciones tienen las tablas V8, pero no sus registros iniciales.
    -- Solo crea el rol si falta; no cambia la politica de un rol ya configurado.
    IF NOT EXISTS (SELECT 1 FROM public.roles_sistema WHERE codigo = 'CAJERO') THEN
        -- Hibernate puede haber creado estas tablas sin DEFAULT de UUID/fechas/activo.
        INSERT INTO public.permisos_sistema (id, codigo, nombre, descripcion, activo, creado_en, actualizado_en)
        SELECT gen_random_uuid(), p.codigo, p.nombre, p.descripcion, TRUE, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP
        FROM (VALUES
            ('VENTAS_VIEW', 'Ver ventas', 'Permite consultar la seccion de ventas.'),
            ('VENTAS_EDIT', 'Registrar ventas', 'Permite registrar ventas manuales.'),
            ('SEPARADOS_VIEW', 'Ver separados', 'Permite consultar separados y sus abonos.'),
            ('SEPARADOS_EDIT', 'Gestionar separados', 'Permite crear separados y registrar abonos.'),
            ('MOVIMIENTOS_VIEW', 'Ver movimientos', 'Permite consultar movimientos de caja.')
        ) AS p(codigo, nombre, descripcion)
        WHERE NOT EXISTS (SELECT 1 FROM public.permisos_sistema existente WHERE existente.codigo = p.codigo)
        ON CONFLICT DO NOTHING;
        IF (SELECT count(*) FROM public.permisos_sistema WHERE activo AND codigo IN (
            'VENTAS_VIEW', 'VENTAS_EDIT', 'SEPARADOS_VIEW', 'SEPARADOS_EDIT', 'MOVIMIENTOS_VIEW')) <> 5 THEN
            RAISE EXCEPTION 'Hay permisos basicos CAJERO desactivados. No se reactivaran automaticamente; revise la seguridad.';
        END IF;
        INSERT INTO public.roles_sistema (id, codigo, nombre, descripcion, activo, creado_en, actualizado_en)
        VALUES (gen_random_uuid(), 'CAJERO', 'Cajero', 'Opera ventas y separados.', TRUE,
            CURRENT_TIMESTAMP, CURRENT_TIMESTAMP) RETURNING id INTO rol_cajero;
        INSERT INTO public.roles_permisos (rol_id, permiso_id)
        SELECT rol_cajero, id FROM public.permisos_sistema WHERE codigo IN (
            'VENTAS_VIEW', 'VENTAS_EDIT', 'SEPARADOS_VIEW', 'SEPARADOS_EDIT', 'MOVIMIENTOS_VIEW');
        RAISE NOTICE 'Rol CAJERO faltante creado con sus cinco permisos operativos basicos.';
    END IF;
    SELECT id INTO rol_cajero FROM public.roles_sistema WHERE codigo = 'CAJERO' AND activo;
    IF rol_cajero IS NULL THEN
        RAISE EXCEPTION 'El rol CAJERO existe pero esta inactivo. No se reactivara automaticamente; revise la seguridad.';
    END IF;
    FOREACH nombre_usuario IN ARRAY ARRAY['willinton', 'usuario'] LOOP
        IF EXISTS (SELECT 1 FROM public.usuarios_sistema WHERE lower(username) = nombre_usuario) THEN
            RAISE NOTICE 'Usuario % ya existe: se conserva su clave, estado y roles.', nombre_usuario;
            CONTINUE;
        END IF;
        nuevo_id := gen_random_uuid();
        INSERT INTO public.usuarios_sistema (id, username, nombre_completo, password_hash, activo, creado_en, actualizado_en)
        VALUES (nuevo_id, nombre_usuario, initcap(nombre_usuario),
            public.crypt('usuario123*', public.gen_salt('bf', 10)), TRUE, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP);
        INSERT INTO public.usuarios_roles (usuario_id, rol_id) VALUES (nuevo_id, rol_cajero);
        RAISE NOTICE 'Usuario % creado con rol CAJERO. Cambie la clave inicial antes de entregar el equipo.', nombre_usuario;
    END LOOP;
END
$usuarios_iniciales$;
DO $fin$
BEGIN
    RAISE NOTICE 'Estructura V12..V23 preparada. Se conservan datos, token, consecutivos y cierres existentes.';
    RAISE NOTICE 'Revise Account ID, token, ambiente, resolucion y proximo numero en el POS antes de habilitar Dataico en PDN.';
    RAISE NOTICE 'Si se creo CONSUMIDOR FINAL, complete su direccion/departamento/ciudad reales. No se copiaron direcciones de desarrollo.';
    RAISE NOTICE 'No se registro historial Flyway. Evite que el arranque ejecute los originales de pruebas; consulte README_PDN_CONSOLIDADO.md.';
END
$fin$;
COMMIT;
