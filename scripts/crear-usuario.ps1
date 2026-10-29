param(
    [Parameter(Mandatory = $true)][string]$Usuario,
    [Parameter(Mandatory = $true)][string]$Nombre,
    [ValidateSet('ADMIN', 'CAJERO', 'COMPRAS', 'AUDITOR', 'OPERACIONES')][string]$Rol = 'ADMIN',
    [string]$Contenedor = 'posdesktop-postgres',
    [string]$BaseDatos = 'posdesktop',
    [string]$UsuarioDb = 'pos'
)

$ErrorActionPreference = 'Stop'
$Usuario = $Usuario.Trim().ToLowerInvariant()
$Nombre = $Nombre.Trim()
if ($Usuario -notmatch '^[a-z0-9._-]{1,80}$') { throw 'Usuario: 1 a 80 letras sin tildes, numeros, punto, guion o guion bajo.' }
if ($Nombre.Length -lt 1 -or $Nombre.Length -gt 160) { throw 'Nombre: entre 1 y 160 caracteres.' }

function Read-PlainSecret([string]$Prompt) {
    $secure = Read-Host $Prompt -AsSecureString
    $pointer = [Runtime.InteropServices.Marshal]::SecureStringToBSTR($secure)
    try { return [Runtime.InteropServices.Marshal]::PtrToStringBSTR($pointer) }
    finally {
        [Runtime.InteropServices.Marshal]::ZeroFreeBSTR($pointer)
        $secure.Dispose()
    }
}

function Encode-SqlValue([string]$Value) {
    $encoded = [Convert]::ToBase64String([Text.Encoding]::UTF8.GetBytes($Value))
    return "convert_from(decode('$encoded', 'base64'), 'UTF8')"
}

$secret = Read-PlainSecret 'Clave del nuevo usuario (minimo 12 caracteres)'
$confirmation = Read-PlainSecret 'Repite la clave'
try {
    if ($secret -cne $confirmation) { throw 'Las claves no coinciden.' }
    if ($secret.Length -lt 12 -or [Text.Encoding]::UTF8.GetByteCount($secret) -gt 72 -or $secret.Contains([char]0)) {
        throw 'Usa al menos 12 caracteres y como maximo 72 bytes UTF-8, sin caracteres NUL.'
    }
    $usernameSql = Encode-SqlValue $Usuario
    $nameSql = Encode-SqlValue $Nombre
    $passwordSql = Encode-SqlValue $secret
    $roleSql = Encode-SqlValue $Rol
    $sql = @"
\set VERBOSITY terse
BEGIN;
CREATE EXTENSION IF NOT EXISTS pgcrypto;
SELECT pg_advisory_xact_lock(hashtextextended('posdesktop_crear_usuario', 0));
DO `$validate`$
BEGIN
    IF NOT EXISTS (SELECT 1 FROM public.roles_sistema WHERE codigo = $roleSql AND activo) THEN
        RAISE EXCEPTION 'El rol solicitado no existe o esta inactivo. Instale primero el esquema de seguridad y sus permisos.';
    END IF;
    IF EXISTS (SELECT 1 FROM public.usuarios_sistema WHERE lower(username) = $usernameSql) THEN
        RAISE EXCEPTION 'El usuario ya existe. No se cambiaron su clave ni sus permisos.';
    END IF;
END
`$validate`$;
WITH nuevo AS (
    INSERT INTO public.usuarios_sistema (id, username, nombre_completo, password_hash, activo)
    VALUES (gen_random_uuid(), $usernameSql, $nameSql, crypt($passwordSql, gen_salt('bf', 10)), TRUE)
    RETURNING id
)
INSERT INTO public.usuarios_roles (usuario_id, rol_id)
SELECT nuevo.id, rol.id FROM nuevo CROSS JOIN public.roles_sistema rol WHERE rol.codigo = $roleSql AND rol.activo;
COMMIT;
"@
    # Solo por stdin: no se escribe la clave en un archivo ni en argumentos de procesos.
    $sql | & docker exec -i $Contenedor psql -X -q -v ON_ERROR_STOP=1 -U $UsuarioDb -d $BaseDatos
    if ($LASTEXITCODE -ne 0) { throw 'No se creo el usuario. Revisa el error de PostgreSQL; la transaccion no se completo.' }
    Write-Host "Usuario '$Usuario' creado con rol '$Rol'. Ya puede ingresar al POS."
}
finally {
    $secret = $null
    $confirmation = $null
    $passwordSql = $null
    $sql = $null
}
