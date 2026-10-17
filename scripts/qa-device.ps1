param(
    [ValidateSet('emulator-5580','emulator-5582')][string]$Serial = 'emulator-5580',
    [ValidateSet('tree','tap','screenshot','scroll')][string]$Action = 'tree',
    [string]$Text = '',
    [ValidatePattern('^[a-z0-9-]+$')][string]$Name = 'screen'
)
$ErrorActionPreference='Stop'
$taskRoot=[IO.Path]::GetFullPath((Join-Path $PSScriptRoot '..'))
$taskAdb=Join-Path $taskRoot '.tools/android-sdk/platform-tools/adb.exe'
if ($Action -eq 'screenshot') {
    & $taskAdb -s $Serial shell -n screencap -p /sdcard/phnomy-qa.png
    $taskDir=Join-Path $taskRoot 'docs/screenshots'
    New-Item -ItemType Directory -Path $taskDir -Force | Out-Null
    & $taskAdb -s $Serial pull /sdcard/phnomy-qa.png (Join-Path $taskDir ($Name+'.png'))
    exit $LASTEXITCODE
}
if ($Action -eq 'scroll') { & $taskAdb -s $Serial shell -n input swipe 550 1900 550 650 400; exit $LASTEXITCODE }
& $taskAdb -s $Serial shell -n uiautomator dump /sdcard/phnomy-qa.xml | Out-Null
$taskRaw=(& $taskAdb -s $Serial shell -n cat /sdcard/phnomy-qa.xml) -join ''
[xml]$taskTree=$taskRaw
$taskNodes=@($taskTree.SelectNodes('//node'))
if ($Action -eq 'tap') {
    $taskNode=$taskNodes | Where-Object { $_.text -eq $Text -or $_.'content-desc' -eq $Text } | Select-Object -First 1
    if (!$taskNode) { throw "No visible node matches: $Text" }
    $taskBounds=[regex]::Matches($taskNode.bounds,'\d+') | ForEach-Object { [int]$_.Value }
    $taskX=[int](($taskBounds[0]+$taskBounds[2])/2)
    $taskY=[int](($taskBounds[1]+$taskBounds[3])/2)
    & $taskAdb -s $Serial shell -n input tap $taskX $taskY
    Write-Output "Tapped $Text at $taskX,$taskY"
} else {
    $taskNodes | Where-Object { $_.text -or $_.'content-desc' } | ForEach-Object {
        [pscustomobject]@{Text=$_.text;Description=$_.'content-desc';Bounds=$_.bounds;Enabled=$_.enabled;Checked=$_.checked}
    } | ConvertTo-Json -Depth 3
}
