$ErrorActionPreference = 'Stop'
$taskRoot = [IO.Path]::GetFullPath((Join-Path $PSScriptRoot '..'))
$taskProperties = Join-Path $taskRoot 'signing.properties'
if (Test-Path -LiteralPath $taskProperties) { Write-Output 'Existing release signing preserved.'; exit 0 }
$taskPrivate = Join-Path $taskRoot '.tools/signing'
New-Item -ItemType Directory -Path $taskPrivate -Force | Out-Null
$taskKey = Join-Path $taskPrivate 'phnomy-release.jks'
if (Test-Path -LiteralPath $taskKey) { throw 'A signing key already exists. Restore its signing.properties instead of replacing it.' }
$taskJava = Get-ChildItem (Join-Path $taskRoot '.tools/jdk') -Directory | Select-Object -First 1
$taskPassword = [Convert]::ToBase64String([Security.Cryptography.RandomNumberGenerator]::GetBytes(32))
$env:PHNOMY_SIGNING_PASSWORD = $taskPassword
try {
    & (Join-Path $taskJava.FullName 'bin/keytool.exe') -genkeypair -keystore $taskKey -storetype JKS -alias phnomy -keyalg RSA -keysize 3072 -sigalg SHA256withRSA -validity 10000 -storepass:env PHNOMY_SIGNING_PASSWORD -keypass:env PHNOMY_SIGNING_PASSWORD -dname 'CN=Phnomy, OU=Android, O=Phnomy, C=TW'
    if ($LASTEXITCODE -ne 0) { throw 'Release signing key generation failed.' }
    $taskLines = @('storeFile=.tools/signing/phnomy-release.jks', ('storePassword=' + $taskPassword), 'keyAlias=phnomy', ('keyPassword=' + $taskPassword))
    [IO.File]::WriteAllLines($taskProperties,$taskLines,[Text.UTF8Encoding]::new($false))
    Write-Output 'Release signing created. Keep .tools/signing and signing.properties private and backed up for future updates.'
} finally { Remove-Item Env:\PHNOMY_SIGNING_PASSWORD -ErrorAction SilentlyContinue }
