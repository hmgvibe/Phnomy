param([ValidateSet(31,36)][int]$Api = 31)
$ErrorActionPreference = 'Stop'
$taskRoot = [IO.Path]::GetFullPath((Join-Path $PSScriptRoot '..'))
$taskTools = Join-Path $taskRoot '.tools'
$env:JAVA_HOME = (Get-ChildItem (Join-Path $taskTools 'jdk') -Directory | Select-Object -First 1).FullName
$env:ANDROID_HOME = Join-Path $taskTools 'android-sdk'
$env:ANDROID_SDK_ROOT = $env:ANDROID_HOME
$env:ANDROID_USER_HOME = Join-Path $taskTools 'android-user'
$env:ANDROID_AVD_HOME = Join-Path $taskTools 'avd'
New-Item -ItemType Directory -Path $env:ANDROID_AVD_HOME -Force | Out-Null
$taskName = "phnomy_api$Api"
$taskAvd = Join-Path $env:ANDROID_AVD_HOME ($taskName + '.avd')
$taskImage = Join-Path $env:ANDROID_HOME "system-images/android-$Api/google_apis/x86_64/system.img"
if (!(Test-Path -LiteralPath $taskImage)) { throw "Android $Api image not installed yet." }
if (!(Test-Path -LiteralPath (Join-Path $taskAvd 'config.ini'))) {
    'no' | & (Join-Path $env:ANDROID_HOME 'cmdline-tools/19.0/bin/avdmanager.bat') create avd -n $taskName -p $taskAvd -k "system-images;android-$Api;google_apis;x86_64" -d pixel_6
    if ($LASTEXITCODE -ne 0) { throw 'AVD creation failed.' }
}
$taskPort = if ($Api -eq 31) { 5580 } else { 5582 }
$taskAdb = Join-Path $env:ANDROID_HOME 'platform-tools/adb.exe'
$taskDevices = & $taskAdb devices
if ($taskDevices -match "emulator-$taskPort\s+device") { Write-Output "emulator-$taskPort is already running."; exit 0 }
$taskLog = Join-Path $taskTools "emulator-$Api.log"
$taskError = Join-Path $taskTools "emulator-$Api-error.log"
Start-Process -FilePath (Join-Path $env:ANDROID_HOME 'emulator/emulator.exe') -WindowStyle Hidden -ArgumentList @('-avd',$taskName,'-port',$taskPort,'-no-window','-no-audio','-no-snapshot','-camera-back','none','-camera-front','none','-gpu','swiftshader','-memory','2048','-cores','2') -RedirectStandardOutput $taskLog -RedirectStandardError $taskError | Out-Null
Write-Output "Started emulator-$taskPort (API $Api); use this emulator serial explicitly for all QA commands."
