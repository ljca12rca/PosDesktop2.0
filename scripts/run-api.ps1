$ErrorActionPreference = 'Stop'
. (Join-Path $PSScriptRoot 'maven-common.ps1')
Push-Location $repoRoot
try {
    Invoke-PosMaven -MavenArguments @('-pl', 'pos-api', '-am', 'package', '-DskipTests')
    [xml]$pom = Get-Content (Join-Path $repoRoot 'pom.xml')
    $apiJar = Join-Path $repoRoot "pos-api/target/pos-api-$($pom.project.version).jar"
    $javaArgs = @('-jar', $apiJar)
    $settings = Join-Path $env:USERPROFILE 'posdesktop-media/config/application.yml'
    if (Test-Path -LiteralPath $settings) {
        $javaArgs += "--spring.config.location=$(([Uri]$settings).AbsoluteUri)"
    }
    & java @javaArgs
    if ($LASTEXITCODE -ne 0) { throw 'No se pudo iniciar la API.' }
} finally {
    Pop-Location
}
