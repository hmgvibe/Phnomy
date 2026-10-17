param([switch]$Emulator)
$ErrorActionPreference = 'Stop'
$taskRoot = [System.IO.Path]::GetFullPath((Join-Path $PSScriptRoot '..'))
$taskTools = Join-Path $taskRoot '.tools'
$taskDownloads = Join-Path $taskTools 'downloads'
New-Item -ItemType Directory -Path $taskDownloads -Force | Out-Null

function Read-RemoteText([string]$Uri) {
    $taskContent = (Invoke-WebRequest -Uri $Uri).Content
    if ($taskContent -is [byte[]]) { return [Text.Encoding]::UTF8.GetString($taskContent) }
    return [string]$taskContent
}
function Download-Verified([string]$Uri, [string]$Path, [string]$Hash, [string]$Algorithm = 'SHA256') {
    if ((Test-Path -LiteralPath $Path) -and ((Get-FileHash -LiteralPath $Path -Algorithm $Algorithm).Hash -eq $Hash)) { return }
    Invoke-WebRequest -Uri $Uri -OutFile $Path
    if ((Get-FileHash -LiteralPath $Path -Algorithm $Algorithm).Hash -ne $Hash) { throw "Checksum mismatch: $Path" }
}

$taskJdkBase = Join-Path $taskTools 'jdk'
$taskJdkHome = Get-ChildItem -LiteralPath $taskJdkBase -Directory -ErrorAction SilentlyContinue | Where-Object { Test-Path -LiteralPath (Join-Path $_.FullName 'bin\java.exe') } | Select-Object -First 1
if (-not $taskJdkHome) {
    $taskJdk = (Invoke-RestMethod -Uri 'https://api.adoptium.net/v3/assets/latest/21/hotspot?architecture=x64&heap_size=normal&image_type=jdk&jvm_impl=hotspot&os=windows&project=jdk&vendor=eclipse')[0].binary.package
    $taskJdkZip = Join-Path $taskDownloads 'jdk21.zip'
    Download-Verified $taskJdk.link $taskJdkZip $taskJdk.checksum
    Expand-Archive -LiteralPath $taskJdkZip -DestinationPath $taskJdkBase -Force
    $taskJdkHome = Get-ChildItem -LiteralPath $taskJdkBase -Directory | Select-Object -First 1
}
$env:JAVA_HOME = $taskJdkHome.FullName

$taskGradle = Join-Path $taskTools 'gradle-8.13\bin\gradle.bat'
if (-not (Test-Path -LiteralPath $taskGradle)) {
    $taskGradleZip = Join-Path $taskDownloads 'gradle-8.13-bin.zip'
    $taskGradleHash = (Read-RemoteText 'https://services.gradle.org/distributions/gradle-8.13-bin.zip.sha256').Trim()
    Download-Verified 'https://services.gradle.org/distributions/gradle-8.13-bin.zip' $taskGradleZip $taskGradleHash
    Expand-Archive -LiteralPath $taskGradleZip -DestinationPath $taskTools -Force
}
$taskSdkRoot = Join-Path $taskTools 'android-sdk'
$taskSdkManager = Join-Path $taskSdkRoot 'cmdline-tools\19.0\bin\sdkmanager.bat'
if (-not (Test-Path -LiteralPath $taskSdkManager)) {
    [xml]$taskSdkMetadata = Read-RemoteText 'https://dl.google.com/android/repository/repository2-1.xml'
    $taskSdkPackage = $taskSdkMetadata.SelectSingleNode("//*[local-name()='remotePackage' and @path='cmdline-tools;19.0']")
    $taskSdkArchive = $taskSdkPackage.archives.archive | Where-Object { $_.'host-os' -eq 'windows' } | Select-Object -First 1
    $taskSdkZip = Join-Path $taskDownloads 'android-commandline-tools-19.zip'
    $taskChecksumNode = $taskSdkArchive.complete.SelectSingleNode('checksum')
    Download-Verified ('https://dl.google.com/android/repository/' + $taskSdkArchive.complete.url) $taskSdkZip $taskChecksumNode.InnerText.Trim() 'SHA1'
    $taskUnpacked = Join-Path $taskTools 'commandline-19-unpacked'
    Expand-Archive -LiteralPath $taskSdkZip -DestinationPath $taskUnpacked -Force
    New-Item -ItemType Directory -Path (Join-Path $taskSdkRoot 'cmdline-tools') -Force | Out-Null
    Copy-Item -LiteralPath (Join-Path $taskUnpacked 'cmdline-tools') -Destination (Join-Path $taskSdkRoot 'cmdline-tools\19.0') -Recurse -Force
}
$env:ANDROID_HOME = $taskSdkRoot
$env:ANDROID_SDK_ROOT = $taskSdkRoot
$env:GRADLE_USER_HOME = Join-Path $taskTools 'gradle-cache'
$env:ANDROID_USER_HOME = Join-Path $taskTools 'android-user'
1..80 | ForEach-Object { 'y' } | & $taskSdkManager --sdk_root=$taskSdkRoot --licenses | Out-Null
& $taskSdkManager --sdk_root=$taskSdkRoot 'platform-tools' 'platforms;android-36' 'build-tools;36.0.0'
if ($LASTEXITCODE -ne 0) { throw 'Android SDK installation failed.' }
if ($Emulator) {
    & $taskSdkManager --sdk_root=$taskSdkRoot 'emulator' 'system-images;android-36;google_apis;x86_64' 'system-images;android-31;google_apis;x86_64'
    if ($LASTEXITCODE -ne 0) { throw 'Emulator installation failed.' }
}
$taskLocalProperties = 'sdk.dir=' + $taskSdkRoot.Replace('\','/').Replace(':','\:')
[IO.File]::WriteAllText((Join-Path $taskRoot 'local.properties'), $taskLocalProperties + [Environment]::NewLine, [Text.UTF8Encoding]::new($false))
[pscustomobject]@{ JavaHome=$env:JAVA_HOME; AndroidSdk=$taskSdkRoot; Gradle=$taskGradle; GradleUserHome=$env:GRADLE_USER_HOME } | ConvertTo-Json
