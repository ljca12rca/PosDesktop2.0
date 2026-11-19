$ErrorActionPreference = 'Stop'
. (Join-Path $PSScriptRoot 'maven-common.ps1')
Push-Location $repoRoot
try {
    Invoke-PosMaven -MavenArguments @('-pl', 'pos-api', '-am', 'package', '-DskipTests')
    Invoke-PosMaven -MavenArguments @('-pl', 'pos-desktop', 'javafx:run')
} finally {
    Pop-Location
}
