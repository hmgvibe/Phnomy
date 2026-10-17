$ErrorActionPreference = 'Stop'
$taskRoot = [IO.Path]::GetFullPath((Join-Path $PSScriptRoot '..'))
$taskArtifacts = Join-Path $taskRoot 'artifacts'
New-Item -ItemType Directory -Path $taskArtifacts -Force | Out-Null
$taskApk = Join-Path $taskRoot 'app/build/outputs/apk/release/app-release.apk'
if (!(Test-Path -LiteralPath $taskApk)) { throw 'Build the signed release APK first.' }
$taskMetadata = Get-Content -LiteralPath (Join-Path $taskRoot 'app/build/outputs/apk/release/output-metadata.json') -Raw | ConvertFrom-Json
$taskVersion = [string]$taskMetadata.elements[0].versionName
if ($taskVersion -notmatch '^\d+\.\d+\.\d+$') { throw 'Invalid release version metadata.' }
$taskJava = Get-ChildItem (Join-Path $taskRoot '.tools/jdk') -Directory | Select-Object -First 1
if ($taskJava) { $env:JAVA_HOME=$taskJava.FullName }
$taskSigner = Join-Path $taskRoot '.tools/android-sdk/build-tools/35.0.0/apksigner.bat'
& $taskSigner verify --verbose --print-certs $taskApk
if ($LASTEXITCODE -ne 0) { throw 'APK signature verification failed.' }
$taskOutput = Join-Path $taskArtifacts ("Phnomy-v$taskVersion.apk")
Copy-Item -LiteralPath $taskApk -Destination $taskOutput -Force
$taskZipPath = Join-Path $taskArtifacts ("Phnomy-v$taskVersion-source.zip")
if (Test-Path -LiteralPath $taskZipPath) { Remove-Item -LiteralPath $taskZipPath -Force }
$taskFiles = @('README.md','.gitignore','settings.gradle.kts','build.gradle.kts','gradle.properties','gradlew','gradlew.bat','app/build.gradle.kts','app/proguard-rules.pro') | ForEach-Object { Join-Path $taskRoot $_ }
foreach ($taskFolder in @('app/src','app/schemas','docs','gradle','licenses','scripts')) {
    $taskFiles += Get-ChildItem -LiteralPath (Join-Path $taskRoot $taskFolder) -File -Recurse | Select-Object -ExpandProperty FullName
}
$taskFiles += @('design/README.md','design/Phnomy-UI-overview.png') | ForEach-Object { Join-Path $taskRoot $_ }
$taskZip = [IO.Compression.ZipFile]::Open($taskZipPath,[IO.Compression.ZipArchiveMode]::Create)
try {
    foreach ($taskFile in $taskFiles) {
        $taskRelative = [IO.Path]::GetRelativePath($taskRoot,$taskFile).Replace('\','/')
        if ($taskRelative -match '(?i)(^|/)(signing.properties|local.properties)$|\.(jks|keystore)$' -or $taskRelative.StartsWith('../')) { throw 'Private or external file in source list.' }
        [IO.Compression.ZipFileExtensions]::CreateEntryFromFile($taskZip,$taskFile,('Phnomy/'+$taskRelative),[IO.Compression.CompressionLevel]::Optimal) | Out-Null
    }
} finally { $taskZip.Dispose() }
$taskHashes = @($taskOutput,$taskZipPath) | ForEach-Object { (Get-FileHash -LiteralPath $_ -Algorithm SHA256).Hash.ToLowerInvariant() + '  ' + [IO.Path]::GetFileName($_) }
[IO.File]::WriteAllLines((Join-Path $taskArtifacts 'SHA256SUMS.txt'),$taskHashes,[Text.UTF8Encoding]::new($false))
Get-Item -LiteralPath $taskOutput,$taskZipPath | Select-Object Name,Length
