# Solo descarga recursos estaticos oficiales OASIS, nunca documentos ni servicios DIAN.
$ErrorActionPreference = 'Stop'
$baseUri = [uri]'https://docs.oasis-open.org/ubl/os-UBL-2.1/xsd/'
$destination = Join-Path (Split-Path $PSScriptRoot -Parent) 'pos-invoicing/src/main/resources/ubl21'
$queue = [Collections.Generic.Queue[uri]]::new()
$visited = [Collections.Generic.HashSet[string]]::new()
$queue.Enqueue([uri]::new($baseUri, 'maindoc/UBL-Invoice-2.1.xsd'))
while ($queue.Count -gt 0) {
    $uri = $queue.Dequeue()
    if (-not $visited.Add($uri.AbsoluteUri)) { continue }
    if (-not $uri.AbsoluteUri.StartsWith($baseUri.AbsoluteUri)) { throw "Importacion externa no autorizada: $uri" }
    $relative = $baseUri.MakeRelativeUri($uri).ToString()
    $target = Join-Path $destination $relative
    $null = New-Item -ItemType Directory -Force -Path (Split-Path $target -Parent)
    Invoke-WebRequest -Uri $uri -OutFile $target
    $settings = [Xml.XmlReaderSettings]::new()
    # El XSD oficial xmldsig contiene entidades internas. Nunca resolver DTD externos.
    $settings.DtdProcessing = [Xml.DtdProcessing]::Parse
    $settings.MaxCharactersFromEntities = 4096
    $settings.XmlResolver = $null
    $reader = [Xml.XmlReader]::Create($target, $settings)
    try {
        $document = [Xml.XmlDocument]::new()
        $document.XmlResolver = $null
        $document.Load($reader)
        foreach ($node in $document.SelectNodes('//*[@schemaLocation]')) {
            $queue.Enqueue([uri]::new($uri, $node.GetAttribute('schemaLocation')))
        }
    } finally { $reader.Dispose() }
}
Write-Output "Esquemas oficiales descargados: $($visited.Count). Validacion posterior sin red."
