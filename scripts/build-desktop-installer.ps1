param(
    [ValidateSet("exe", "msi", "app-image")]
    [string]$Type = "exe"
)

$ErrorActionPreference = "Stop"

$scriptDir = Split-Path -Parent $MyInvocation.MyCommand.Path
$repoRoot = Split-Path -Parent $scriptDir
$toolsDir = Join-Path $repoRoot ".tools"
$mavenVersion = "3.9.9"
$mavenHome = Join-Path $toolsDir "apache-maven-$mavenVersion"
$mavenZip = Join-Path $toolsDir "apache-maven-$mavenVersion-bin.zip"
$wixVersion = "314"
$wixDir = Join-Path $toolsDir "wix-$wixVersion"
$wixZip = Join-Path $toolsDir "wix$wixVersion-binaries.zip"
$desktopModuleDir = Join-Path $repoRoot "pos-desktop"
$desktopTargetDir = Join-Path $desktopModuleDir "target"
$installerRoot = Join-Path $desktopTargetDir "installer"
$installerInput = Join-Path $installerRoot "input"
$installerDist = Join-Path $installerRoot "dist"

function Write-Step {
    param([string]$Message)
    Write-Host ""
    Write-Host "==> $Message" -ForegroundColor Cyan
}

function Ensure-Directory {
    param([string]$Path)
    if (-not (Test-Path $Path)) {
        New-Item -ItemType Directory -Path $Path | Out-Null
    }
}

function Remove-DirectorySafe {
    param([string]$Path)
    if (Test-Path $Path) {
        Remove-Item -Recurse -Force $Path
    }
}

function Invoke-Download {
    param(
        [string]$Uri,
        [string]$Destination
    )
    Write-Host "Descargando $Uri"
    Invoke-WebRequest -Uri $Uri -OutFile $Destination
}

function Ensure-Maven {
    $command = Get-Command mvn.cmd -ErrorAction SilentlyContinue
    if ($command) {
        return $command.Source
    }

    Ensure-Directory $toolsDir
    if (-not (Test-Path $mavenHome)) {
        if (-not (Test-Path $mavenZip)) {
            Invoke-Download `
                -Uri "https://archive.apache.org/dist/maven/maven-3/$mavenVersion/binaries/apache-maven-$mavenVersion-bin.zip" `
                -Destination $mavenZip
        }
        Expand-Archive -Path $mavenZip -DestinationPath $toolsDir -Force
    }

    $mavenCmd = Join-Path $mavenHome "bin\mvn.cmd"
    if (-not (Test-Path $mavenCmd)) {
        throw "No se encontro Maven en $mavenCmd."
    }
    return $mavenCmd
}

function Ensure-Wix {
    if ($Type -eq "app-image") {
        return
    }

    $candle = Get-Command candle.exe -ErrorAction SilentlyContinue
    $light = Get-Command light.exe -ErrorAction SilentlyContinue
    if ($candle -and $light) {
        return
    }

    Ensure-Directory $toolsDir
    if (-not (Test-Path $wixDir)) {
        if (-not (Test-Path $wixZip)) {
            Invoke-Download `
                -Uri "https://github.com/wixtoolset/wix3/releases/download/wix314rtm/wix314-binaries.zip" `
                -Destination $wixZip
        }
        Expand-Archive -Path $wixZip -DestinationPath $wixDir -Force
    }

    if (-not (Test-Path (Join-Path $wixDir "candle.exe")) -or -not (Test-Path (Join-Path $wixDir "light.exe"))) {
        throw "WiX no quedo disponible en $wixDir."
    }

    $env:Path = "$wixDir;$env:Path"
}

function Get-ProjectVersion {
    [xml]$pom = Get-Content (Join-Path $repoRoot "pom.xml")
    $version = $pom.project.version
    if ([string]::IsNullOrWhiteSpace($version)) {
        throw "No se pudo leer la version del proyecto desde pom.xml."
    }
    return $version.Replace("-SNAPSHOT", "")
}

function Get-DesktopJarName {
    [xml]$pom = Get-Content (Join-Path $desktopModuleDir "pom.xml")
    $artifactId = $pom.project.artifactId
    $parentVersion = $pom.project.parent.version
    if ([string]::IsNullOrWhiteSpace($artifactId) -or [string]::IsNullOrWhiteSpace($parentVersion)) {
        throw "No se pudo resolver el nombre del JAR de pos-desktop."
    }
    return "$artifactId-$parentVersion.jar"
}

function Convert-PngToIco {
    param(
        [string]$PngPath,
        [string]$IcoPath
    )

    if (-not (Test-Path $PngPath)) {
        throw "No se encontro el icono PNG en $PngPath."
    }

    Add-Type -AssemblyName System.Drawing
    $image = [System.Drawing.Image]::FromFile((Resolve-Path $PngPath))
    try {
        $width = if ($image.Width -ge 256) { 0 } else { [byte]$image.Width }
        $height = if ($image.Height -ge 256) { 0 } else { [byte]$image.Height }
    } finally {
        $image.Dispose()
    }

    [byte[]]$pngBytes = [System.IO.File]::ReadAllBytes((Resolve-Path $PngPath))
    Ensure-Directory (Split-Path -Parent $IcoPath)

    $stream = [System.IO.File]::Open($IcoPath, [System.IO.FileMode]::Create, [System.IO.FileAccess]::Write)
    $writer = New-Object System.IO.BinaryWriter($stream)

    try {
        $writer.Write([UInt16]0)
        $writer.Write([UInt16]1)
        $writer.Write([UInt16]1)
        $writer.Write([byte]$width)
        $writer.Write([byte]$height)
        $writer.Write([byte]0)
        $writer.Write([byte]0)
        $writer.Write([UInt16]1)
        $writer.Write([UInt16]32)
        $writer.Write([UInt32]$pngBytes.Length)
        $writer.Write([UInt32]22)
        $writer.Write($pngBytes)
    } finally {
        $writer.Dispose()
        $stream.Dispose()
    }
}

function Invoke-Maven {
    param(
        [string]$MavenCmd,
        [string[]]$Arguments
    )

    & $MavenCmd @Arguments
    if ($LASTEXITCODE -ne 0) {
        throw "Maven fallo ejecutando: $($Arguments -join ' ')"
    }
}

Ensure-Directory $toolsDir

Write-Step "Resolviendo Maven"
$mavenCmd = Ensure-Maven

Write-Step "Validando WiX para jpackage"
Ensure-Wix

Write-Step "Preparando directorios de empaquetado"
Remove-DirectorySafe $installerRoot
Ensure-Directory $installerInput
Ensure-Directory $installerDist

Write-Step "Compilando el modulo desktop"
Invoke-Maven -MavenCmd $mavenCmd -Arguments @("-pl", "pos-desktop", "-am", "clean", "package", "-DskipTests")

Write-Step "Copiando dependencias runtime del desktop"
Invoke-Maven -MavenCmd $mavenCmd -Arguments @("-pl", "pos-desktop", "dependency:copy-dependencies", "-DincludeScope=runtime", "-DoutputDirectory=target/installer/input")

$desktopJarName = Get-DesktopJarName
$desktopJar = Join-Path $desktopTargetDir $desktopJarName
if (-not (Test-Path $desktopJar)) {
    throw "No se encontro el JAR principal del desktop en $desktopJar."
}

Copy-Item -Path $desktopJar -Destination (Join-Path $installerInput $desktopJarName) -Force

$appVersion = Get-ProjectVersion
$appName = "POS Desktop"
$iconSourcePath = Join-Path $repoRoot "ico.png"
$iconPath = Join-Path $installerRoot "pos-desktop.ico"

if (Test-Path $iconSourcePath) {
    Write-Step "Convirtiendo icono PNG a ICO"
    Convert-PngToIco -PngPath $iconSourcePath -IcoPath $iconPath
}

$jpackageArgs = @(
    "--type", $Type,
    "--input", $installerInput,
    "--dest", $installerDist,
    "--name", $appName,
    "--main-jar", $desktopJarName,
    "--main-class", "com.posdesktop.pos.PosDesktopApplication",
    "--app-version", $appVersion,
    "--vendor", "ljca12rca",
    "--description", "POS Desktop UI",
    "--copyright", "Copyright 2026",
    "--java-options", "-Dfile.encoding=UTF-8"
)

if ($Type -ne "app-image") {
    $jpackageArgs += @("--win-menu", "--win-shortcut", "--win-dir-chooser", "--win-per-user-install")
}

if (Test-Path $iconPath) {
    $jpackageArgs += @("--icon", $iconPath)
}

Write-Step "Generando instalador con jpackage"
& jpackage @jpackageArgs
if ($LASTEXITCODE -ne 0) {
    throw "jpackage fallo generando el instalador."
}

Write-Host ""
Write-Host "Instalador generado en:" -ForegroundColor Green
Get-ChildItem -Path $installerDist | Select-Object FullName, Length
