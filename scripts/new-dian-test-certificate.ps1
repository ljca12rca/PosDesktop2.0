[CmdletBinding()]
param(
    [string]$OutputPath = (Join-Path $PSScriptRoot '..\pos-invoicing\target\test-certificate\posdesktop-local-test.p12'),
    [Security.SecureString]$Password
)
$ErrorActionPreference = 'Stop'
$keytool = if ($env:JAVA_HOME) { Join-Path $env:JAVA_HOME 'bin\keytool.exe' } else { (Get-Command keytool -ErrorAction Stop).Source }
$certificatePath = [IO.Path]::GetFullPath($OutputPath)
if (Test-Path -LiteralPath $certificatePath) { throw 'El archivo ya existe. Usa otro nombre para no reemplazar un certificado.' }
if (-not $Password) { $Password = Read-Host 'Contrasena para el certificado LOCAL de prueba (minimo 6 caracteres)' -AsSecureString }
$pointer = [Runtime.InteropServices.Marshal]::SecureStringToBSTR($Password)
$previousPassword = $env:POSDESKTOP_LOCAL_CERT_PASS
try {
    $env:POSDESKTOP_LOCAL_CERT_PASS = [Runtime.InteropServices.Marshal]::PtrToStringBSTR($pointer)
    if ($env:POSDESKTOP_LOCAL_CERT_PASS.Length -lt 6) { throw 'La contrasena debe tener al menos 6 caracteres.' }
    New-Item -ItemType Directory -Path ([IO.Path]::GetDirectoryName($certificatePath)) -Force | Out-Null
    & $keytool -genkeypair -alias posdesktop-local-test -keyalg RSA -keysize 3072 -sigalg SHA256withRSA `
        -storetype PKCS12 -keystore $certificatePath -storepass:env POSDESKTOP_LOCAL_CERT_PASS -keypass:env POSDESKTOP_LOCAL_CERT_PASS `
        -dname 'CN=SOLO PRUEBAS LOCALES - NO VALIDO DIAN, O=POSDesktop TEST, C=CO' `
        -startdate '-1d' -validity 90 -ext 'KU=digitalSignature,nonRepudiation' -noprompt
    if ($LASTEXITCODE -ne 0) { throw 'No fue posible generar el certificado local.' }
    Write-Output "Certificado LOCAL generado: $certificatePath"
    Write-Output 'Importalo en Configuracion electronica > PRUEBAS con la contrasena elegida. No es valido para emitir ante DIAN.'
} finally {
    $env:POSDESKTOP_LOCAL_CERT_PASS = $previousPassword
    [Runtime.InteropServices.Marshal]::ZeroFreeBSTR($pointer)
}
