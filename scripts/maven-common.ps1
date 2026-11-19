$repoRoot = Split-Path -Parent $PSScriptRoot

function Get-PosMaven {
    $command = Get-Command mvn -ErrorAction SilentlyContinue
    if ($command) { return $command.Source }
    $bundled = Join-Path $repoRoot '.tools/apache-maven-3.9.9/bin/mvn.cmd'
    if (Test-Path -LiteralPath $bundled) { return $bundled }
    throw 'No se encontró Maven. Instálalo o ejecuta build-desktop-installer.ps1 para preparar las herramientas.'
}

function Invoke-PosMaven {
    param([string[]]$MavenArguments)
    & (Get-PosMaven) @MavenArguments
    if ($LASTEXITCODE -ne 0) { throw "Maven falló: $($MavenArguments -join ' ')" }
}
