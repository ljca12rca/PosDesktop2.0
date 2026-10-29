param(
    [string]$Container = "posdesktop-postgres",
    [string]$DbUser = "pos"
)

# Solo crea y elimina UNA base temporal con nombre generado. Nunca usa posdesktop como destino.
$ErrorActionPreference = "Stop"
$testDatabase = "pos_pdn_test_" + [guid]::NewGuid().ToString("N")
$repoRoot = Split-Path (Split-Path $PSScriptRoot -Parent) -Parent
$scriptFile = Join-Path $PSScriptRoot "20261023_pdn_consolidado_pendientes_git_seguro.sql"
$created = $false

function Assert-TestDatabase {
    if ($testDatabase -notmatch '^pos_pdn_test_[a-f0-9]{32}$') {
        throw "Destino de prueba no valido. No se ejecutara SQL."
    }
}

function Invoke-TestSql([string]$Sql, [switch]$MustFail) {
    Assert-TestDatabase
    $output = $Sql | & docker exec -i $Container psql -X -q -A -t -v ON_ERROR_STOP=1 -U $DbUser -d $testDatabase 2>&1
    $code = $LASTEXITCODE
    if ($MustFail) {
        if ($code -eq 0) { throw "Se esperaba rollback por estructura incompatible y la ejecucion fue exitosa." }
        return
    }
    if ($code -ne 0) { throw ($output -join "`n") }
    return ($output -join "`n").Trim()
}

function Assert-Equal([string]$Before, [string]$After, [string]$Message) {
    if ($Before -cne $After) { throw $Message }
}

try {
    Assert-TestDatabase
    & docker exec $Container createdb -U $DbUser $testDatabase
    if ($LASTEXITCODE -ne 0) { throw "No se pudo crear la base temporal de pruebas." }
    $created = $true
    Write-Host "Base temporal: $testDatabase (sin tocar la base real)."

    $baseline = Get-ChildItem (Join-Path $repoRoot "pos-api/src/main/resources/db/migration") -Filter "V*.sql" |
        Where-Object { $_.BaseName -match '^V(\d+)__' -and [int]$Matches[1] -le 11 } |
        Sort-Object { [int]([regex]::Match($_.BaseName, '^V(\d+)').Groups[1].Value) }
    if ($baseline.Count -ne 11) { throw "No se encontraron exactamente las migraciones base V1..V11." }
    foreach ($file in $baseline) {
        # El ; de cierre tambien permite el SELECT final sin ; del archivo V3 original.
        $null = Invoke-TestSql ((Get-Content -Raw -LiteralPath $file.FullName) + "`n;")
    }
    $null = Invoke-TestSql @'
CREATE FUNCTION public.pdn_test_huellas(excluir TEXT[] DEFAULT '{}', ignorar TEXT[] DEFAULT '{}')
RETURNS TEXT LANGUAGE plpgsql AS $$
DECLARE t RECORD; resultado JSONB := '{}'; huella TEXT;
BEGIN
    FOR t IN SELECT tablename FROM pg_tables WHERE schemaname = 'public' AND NOT (tablename = ANY(excluir)) ORDER BY tablename LOOP
        EXECUTE format('SELECT md5(COALESCE(jsonb_agg(to_jsonb(x) - $1 ORDER BY (to_jsonb(x) - $1)::text)::text, ''[]'')) FROM public.%I x', t.tablename)
            INTO huella USING ignorar;
        resultado := resultado || jsonb_build_object(t.tablename, huella);
    END LOOP;
    RETURN resultado::TEXT;
END
$$;
INSERT INTO proveedores (nit, nombre, observacion) VALUES ('PDN-PRUEBA-001', 'Proveedor que debe conservarse', 'No cambiar');
-- Reproduce tablas creadas por Hibernate sin valores por defecto de seguridad.
ALTER TABLE permisos_sistema ALTER COLUMN id DROP DEFAULT, ALTER COLUMN activo DROP DEFAULT,
    ALTER COLUMN creado_en DROP DEFAULT, ALTER COLUMN actualizado_en DROP DEFAULT;
ALTER TABLE roles_sistema ALTER COLUMN id DROP DEFAULT, ALTER COLUMN activo DROP DEFAULT,
    ALTER COLUMN creado_en DROP DEFAULT, ALTER COLUMN actualizado_en DROP DEFAULT;
ALTER TABLE usuarios_sistema ALTER COLUMN id DROP DEFAULT, ALTER COLUMN activo DROP DEFAULT,
    ALTER COLUMN creado_en DROP DEFAULT, ALTER COLUMN actualizado_en DROP DEFAULT;
INSERT INTO cierres_diarios (id, fecha_operacion, fecha_hora_cierre, estado, cantidad_ventas, total_calculado,
    total_final, responsable_usuario_id)
VALUES ('aaaaaaaa-aaaa-4aaa-8aaa-aaaaaaaaaaaa', DATE '2026-01-02', TIMESTAMP '2026-01-02 19:00', 'CERRADO',
    99, 333, 555, (SELECT id FROM usuarios_sistema ORDER BY username LIMIT 1));
INSERT INTO ventas (id, numero_venta, fecha_venta, estado, origen, total, monto_recibido, medio_pago, cierre_diario_id)
VALUES ('bbbbbbbb-bbbb-4bbb-8bbb-bbbbbbbbbbbb', 'PDN-VENTA-CONSERVAR', TIMESTAMP '2026-01-02 10:00',
    'REGISTRADA', 'MANUAL', 9001, 9001, 'TRANSFERENCIA_QR', 'aaaaaaaa-aaaa-4aaa-8aaa-aaaaaaaaaaaa');
'@
    $consolidado = Get-Content -Raw -LiteralPath $scriptFile
    $usersQuery = "SELECT md5(jsonb_agg(to_jsonb(u) ORDER BY u.id)::text) FROM usuarios_sistema u WHERE lower(username) NOT IN ('willinton','usuario');"
    $rolesQuery = "SELECT md5(jsonb_agg(to_jsonb(r) ORDER BY r.usuario_id,r.rol_id)::text) FROM usuarios_roles r JOIN usuarios_sistema u ON u.id=r.usuario_id WHERE lower(u.username) NOT IN ('willinton','usuario');"
    $usersBefore = Invoke-TestSql $usersQuery
    $rolesBefore = Invoke-TestSql $rolesQuery
    $baseBefore = Invoke-TestSql "SELECT public.pdn_test_huellas(ARRAY['clientes_electronicos','facturas_electronicas','dataico_configuracion','dataico_consecutivos','usuarios_sistema','usuarios_roles']);"
    $null = Invoke-TestSql $consolidado
    $baseAfter = Invoke-TestSql "SELECT public.pdn_test_huellas(ARRAY['clientes_electronicos','facturas_electronicas','dataico_configuracion','dataico_consecutivos','usuarios_sistema','usuarios_roles']);"
    Assert-Equal $baseBefore $baseAfter "La primera ejecucion modifico filas de la instalacion V1..V11."
    Assert-Equal $usersBefore (Invoke-TestSql $usersQuery) "Se modificaron usuarios existentes."
    Assert-Equal $rolesBefore (Invoke-TestSql $rolesQuery) "Se modificaron roles de usuarios existentes."
    Assert-Equal "2" (Invoke-TestSql "SELECT count(*) FROM usuarios_sistema u JOIN usuarios_roles ur ON ur.usuario_id=u.id JOIN roles_sistema r ON r.id=ur.rol_id WHERE u.username IN ('willinton','usuario') AND u.activo AND r.codigo='CAJERO' AND crypt('usuario123*',u.password_hash)=u.password_hash;") "No se crearon los usuarios con BCrypt y rol CAJERO."
    Write-Host "OK: usuarios iniciales creados con BCrypt; usuarios y permisos previos conservados."
    Assert-Equal "f|0|1|t" (Invoke-TestSql @'
SELECT (SELECT habilitado FROM dataico_configuracion WHERE id=1),
    (SELECT count(*) FROM dataico_consecutivos),
    (SELECT count(*) FROM clientes_electronicos WHERE identificacion='222222222222'),
    (SELECT direccion IS NULL FROM clientes_electronicos WHERE identificacion='222222222222');
'@) "Se inicializaron datos de pruebas o no se aplicaron los valores seguros."
    Write-Host "OK: primera instalacion conserva ventas, cierres, proveedores, usuarios y demas filas base."

    $null = Invoke-TestSql @'
UPDATE usuarios_sistema SET username='Willinton', password_hash=crypt('otra-clave-de-prueba',gen_salt('bf',10)), activo=FALSE WHERE username='willinton';
UPDATE clientes_electronicos SET direccion='DIRECCION REAL PDN CONSERVAR', email='cliente@example.test'
WHERE identificacion='222222222222';
INSERT INTO dataico_consecutivos VALUES ('FEE',3210);
UPDATE dataico_configuracion SET prefijo='FEE', resolucion='PDN-RES-CONSERVAR', ambiente='PRODUCCION',
    flexible=FALSE, version=9, token_cifrado='cifrado-ficticio-no-real', account_id='11111111-1111-4111-8111-111111111111',
    habilitado=TRUE, base_url='https://api.dataico.com/direct/dataico_api/v2', connect_timeout_seconds=7, read_timeout_seconds=25
WHERE id=1;
INSERT INTO facturas_electronicas (venta_id, fecha_emision, numeracion, numero_factura, cliente_nombre,
    cliente_identificacion, total, estado_dian, estado_cliente, estado_correo, cliente_email)
VALUES ('bbbbbbbb-bbbb-4bbb-8bbb-bbbbbbbbbbbb', TIMESTAMP '2026-01-02 10:00', 'FEE', 'FEE40', 'CONSUMIDOR FINAL',
    '222222222222', 9001, 'DIAN_ACEPTADO', 'REGISTRADO', 'ENVIADO', NULL);
'@
    $before = Invoke-TestSql "SELECT public.pdn_test_huellas();"
    $null = Invoke-TestSql $consolidado
    $null = Invoke-TestSql $consolidado
    Assert-Equal $before (Invoke-TestSql "SELECT public.pdn_test_huellas();") "Repetir el script modifico registros existentes."
    Write-Host "OK: ejecucion repetida conserva TODAS las filas, token, consecutivo, consumidor final y correo NULL historico."

    # Simula una aplicacion parcial: faltan solo columnas opcionales, con tablas ya pobladas.
    $null = Invoke-TestSql @'
ALTER TABLE facturas_electronicas DROP COLUMN cliente_email;
ALTER TABLE dataico_configuracion DROP COLUMN read_timeout_seconds;
'@
    $before = Invoke-TestSql "SELECT public.pdn_test_huellas('{}', ARRAY['cliente_email','read_timeout_seconds']);"
    $null = Invoke-TestSql $consolidado
    Assert-Equal $before (Invoke-TestSql "SELECT public.pdn_test_huellas('{}', ARRAY['cliente_email','read_timeout_seconds']);") "Completar columnas opcionales altero campos existentes."
    Assert-Equal "2" (Invoke-TestSql "SELECT count(*) FROM information_schema.columns WHERE table_schema='public' AND ((table_name='facturas_electronicas' AND column_name='cliente_email') OR (table_name='dataico_configuracion' AND column_name='read_timeout_seconds'));") "No se completaron las columnas faltantes."
    Write-Host "OK: completa columnas opcionales faltantes sin modificar los campos preexistentes."

    # Reproduce la instalacion reportada: tablas de seguridad sin el rol inicial CAJERO.
    # Estas eliminaciones son exclusivamente fixtures de la base temporal generada.
    $null = Invoke-TestSql "DELETE FROM roles_sistema WHERE codigo='CAJERO'; DELETE FROM permisos_sistema WHERE codigo='MOVIMIENTOS_VIEW';"
    $usersBefore = Invoke-TestSql "SELECT md5(jsonb_agg(to_jsonb(u) ORDER BY u.id)::text) FROM usuarios_sistema u;"
    $null = Invoke-TestSql $consolidado
    Assert-Equal $usersBefore (Invoke-TestSql "SELECT md5(jsonb_agg(to_jsonb(u) ORDER BY u.id)::text) FROM usuarios_sistema u;") "Crear el rol faltante altero usuarios existentes."
    Assert-Equal "5" (Invoke-TestSql "SELECT count(*) FROM roles_permisos rp JOIN roles_sistema r ON r.id=rp.rol_id WHERE r.codigo='CAJERO' AND r.activo;") "No se crearon los cinco permisos del rol faltante."
    $before = Invoke-TestSql "SELECT public.pdn_test_huellas();"
    $null = Invoke-TestSql $consolidado
    Assert-Equal $before (Invoke-TestSql "SELECT public.pdn_test_huellas();") "La repeticion modifico roles o usuarios."
    $null = Invoke-TestSql "UPDATE roles_sistema SET activo=FALSE WHERE codigo='CAJERO';"
    $before = Invoke-TestSql "SELECT public.pdn_test_huellas();"
    Invoke-TestSql $consolidado -MustFail
    Assert-Equal $before (Invoke-TestSql "SELECT public.pdn_test_huellas();") "Se reactivo o modifico un rol inactivo."
    $null = Invoke-TestSql "UPDATE roles_sistema SET activo=TRUE WHERE codigo='CAJERO';"
    Write-Host "OK: crea CAJERO faltante sin alterar usuarios; conserva roles desactivados y repeticiones."

    # Una columna obligatoria faltante no tiene un relleno seguro. Debe abortar TODO.
    $null = Invoke-TestSql @'
ALTER TABLE clientes_electronicos DROP COLUMN email;
ALTER TABLE facturas_electronicas DROP COLUMN fecha_emision;
'@
    $before = Invoke-TestSql "SELECT public.pdn_test_huellas();"
    Invoke-TestSql $consolidado -MustFail
    Assert-Equal $before (Invoke-TestSql "SELECT public.pdn_test_huellas();") "El error no revirtio los cambios de datos."
    Assert-Equal "0" (Invoke-TestSql "SELECT count(*) FROM information_schema.columns WHERE table_schema='public' AND table_name='clientes_electronicos' AND column_name='email';") "No se revirtio el DDL anterior al error."
    Write-Host "OK: estructura incompatible aborta; rollback de datos Y de columnas agregadas anteriormente."
    Write-Host "TODAS LAS VERIFICACIONES PASARON. No se aplico el consolidado a ninguna base real."
}
finally {
    if ($created) {
        Assert-TestDatabase
        & docker exec $Container dropdb -U $DbUser --force $testDatabase
        if ($LASTEXITCODE -ne 0) {
            Write-Warning "No se pudo eliminar la base TEMPORAL $testDatabase. No es la base real."
        } else {
            Write-Host "Base temporal eliminada: $testDatabase."
        }
    }
}
