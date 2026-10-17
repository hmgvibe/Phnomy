param([string[]]$Tasks = @('assembleDebug','testDebugUnitTest','lintDebug'))
$ErrorActionPreference = 'Stop'
$taskRoot = [IO.Path]::GetFullPath((Join-Path $PSScriptRoot '..'))
$taskTools = Join-Path $taskRoot '.tools'
$taskJava = Get-ChildItem (Join-Path $taskTools 'jdk') -Directory | Where-Object { Test-Path (Join-Path $_.FullName 'bin/java.exe') } | Select-Object -First 1
if ($taskJava) { $env:JAVA_HOME = $taskJava.FullName }
if (Test-Path (Join-Path $taskTools 'android-sdk')) { $env:ANDROID_HOME = Join-Path $taskTools 'android-sdk' }
$env:ANDROID_SDK_ROOT = $env:ANDROID_HOME
$env:GRADLE_USER_HOME = Join-Path $taskTools 'gradle-cache'
$env:ANDROID_USER_HOME = Join-Path $taskTools 'android-user'
$env:ANDROID_AVD_HOME = Join-Path $taskTools 'avd'
$taskGradle = Join-Path $taskTools 'gradle-8.13/bin/gradle.bat'
if (!(Test-Path $taskGradle)) { $taskGradle = Join-Path $taskRoot 'gradlew.bat' }
Push-Location $taskRoot
try { & $taskGradle --no-daemon --console=plain @Tasks; if ($LASTEXITCODE -ne 0) { throw "Gradle failed ($LASTEXITCODE)" } }
finally { Pop-Location }
