#requires -Version 7.4
[CmdletBinding()]
param(
    [Parameter(Mandatory)][ValidateSet('Backup','Restore')][string]$Mode,
    [Parameter(Mandatory)][string]$BackupFile,
    [string]$SecretsDirectory = (Join-Path $env:LOCALAPPDATA 'PosDesktop/secrets'),
    [Security.SecureString]$Password
)
$ErrorActionPreference = 'Stop'
if (-not $Password) { $Password = Read-Host 'Contrasena del respaldo (minimo 12 caracteres)' -AsSecureString }
$pointer = [Runtime.InteropServices.Marshal]::SecureStringToBSTR($Password)
$plainPassword = $null; $key = $null; $plain = $null; $archive = $null; $aes = $null
try {
    $plainPassword = [Runtime.InteropServices.Marshal]::PtrToStringBSTR($pointer)
    if ($plainPassword.Length -lt 12) { throw 'Usa una contrasena de al menos 12 caracteres.' }
    $file = [IO.Path]::GetFullPath($BackupFile)
    $directory = [IO.Path]::GetFullPath($SecretsDirectory)
    $allowed = @('fiscal.key','delivery.key','dataico-token.key')
    $magic = [Text.Encoding]::ASCII.GetBytes('POSFISCALBKP1')
    if ($Mode -eq 'Backup') {
        if (Test-Path -LiteralPath $file) { throw 'El respaldo ya existe. Usa otro nombre para conservarlo.' }
        $archive = [IO.MemoryStream]::new()
        $zip = [IO.Compression.ZipArchive]::new($archive,[IO.Compression.ZipArchiveMode]::Create,$true)
        $count = 0
        try {
            foreach ($name in $allowed) {
                $source = Join-Path $directory $name
                if (Test-Path -LiteralPath $source -PathType Leaf) {
                    if ((Get-Item -LiteralPath $source).Attributes -band [IO.FileAttributes]::ReparsePoint) { throw 'No se respaldan enlaces a claves externas.' }
                    $bytes = [IO.File]::ReadAllBytes($source)
                    try { if ($bytes.Length -gt 4096) { throw 'Archivo de clave inesperadamente grande.' }; $stream = $zip.CreateEntry($name).Open(); try { $stream.Write($bytes) } finally { $stream.Dispose() }; $count++ }
                    finally { [Array]::Clear($bytes) }
                }
            }
        } finally { $zip.Dispose() }
        if ($count -eq 0) { throw 'No se encontraron claves en la carpeta indicada. Revisa las rutas configuradas en el API.' }
        $plain = $archive.ToArray()
        $salt = [Security.Cryptography.RandomNumberGenerator]::GetBytes(16)
        $nonce = [Security.Cryptography.RandomNumberGenerator]::GetBytes(12)
        $key = [Security.Cryptography.Rfc2898DeriveBytes]::Pbkdf2($plainPassword,$salt,600000,[Security.Cryptography.HashAlgorithmName]::SHA256,32)
        $aes = [Security.Cryptography.AesGcm]::new($key,16)
        $cipher = [byte[]]::new($plain.Length); $tag = [byte[]]::new(16)
        $aes.Encrypt($nonce,$plain,$cipher,$tag,$magic)
        $parent = [IO.Path]::GetDirectoryName($file); [IO.Directory]::CreateDirectory($parent) | Out-Null
        $output = [IO.File]::Open($file,[IO.FileMode]::CreateNew,[IO.FileAccess]::Write)
        try { foreach ($part in @($magic,$salt,$nonce,$tag,$cipher)) { $output.Write([byte[]]$part) } } finally { $output.Dispose() }
        Write-Host "Respaldo cifrado creado. Claves incluidas: $count. Tambien conserva el respaldo PostgreSQL y esta contrasena."
    } else {
        $packed = [IO.File]::ReadAllBytes($file)
        $header = $magic.Length
        if ($packed.Length -lt ($header+45) -or $packed.Length -gt 20000 -or [Text.Encoding]::ASCII.GetString($packed,0,$header) -ne 'POSFISCALBKP1') { throw 'Archivo de respaldo no reconocido.' }
        $salt = $packed[$header..($header+15)]; $nonce = $packed[($header+16)..($header+27)]; $tag = $packed[($header+28)..($header+43)]; $cipher = $packed[($header+44)..($packed.Length-1)]
        $key = [Security.Cryptography.Rfc2898DeriveBytes]::Pbkdf2($plainPassword,[byte[]]$salt,600000,[Security.Cryptography.HashAlgorithmName]::SHA256,32)
        $aes = [Security.Cryptography.AesGcm]::new($key,16); $plain = [byte[]]::new($cipher.Length)
        $aes.Decrypt([byte[]]$nonce,[byte[]]$cipher,[byte[]]$tag,$plain,$magic)
        $archive = [IO.MemoryStream]::new($plain,$false); $zip = [IO.Compression.ZipArchive]::new($archive,[IO.Compression.ZipArchiveMode]::Read)
        $entries = @()
        try {
            if ($zip.Entries.Count -lt 1 -or $zip.Entries.Count -gt 3) { throw 'Cantidad de claves invalida.' }
            foreach ($entry in $zip.Entries) {
                if ($entry.FullName -notin $allowed -or $entry.Length -gt 4096 -or $entry.FullName -in $entries) { throw 'Entrada de respaldo no permitida.' }
                $entries += $entry.FullName
                $target = Join-Path $directory $entry.FullName
                if (Test-Path -LiteralPath $target) { throw "La clave $($entry.FullName) ya existe. No se sobrescribira. Restaura primero en una carpeta nueva y verifica el respaldo." }
            }
            [IO.Directory]::CreateDirectory($directory) | Out-Null
            foreach ($entry in $zip.Entries) {
                $inputStream = $entry.Open(); $output = [IO.File]::Open((Join-Path $directory $entry.FullName),[IO.FileMode]::CreateNew,[IO.FileAccess]::Write)
                try { $inputStream.CopyTo($output) } finally { $inputStream.Dispose(); $output.Dispose() }
            }
        } finally { $zip.Dispose() }
        Write-Host 'Claves restauradas sin sobrescribir archivos. Revisa permisos de la carpeta y las rutas del API antes de iniciarlo.'
    }
} finally {
    [Runtime.InteropServices.Marshal]::ZeroFreeBSTR($pointer)
    $plainPassword = $null
    if ($aes) { $aes.Dispose() }; if ($archive) { $archive.Dispose() }
    if ($key) { [Array]::Clear($key) }; if ($plain) { [Array]::Clear($plain) }
}
