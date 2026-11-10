param([string]$Adb = 'adb', [string]$Serial, [string]$Apk = (Join-Path $PSScriptRoot '../app/build/outputs/apk/debug/app-debug.apk'))
$ErrorActionPreference = 'Stop'
if (-not (Test-Path -LiteralPath $Apk)) { throw 'Сначала соберите debug APK' }
$deviceArgs = @()
if ($Serial) { $deviceArgs = @('-s', $Serial) }
& $Adb @deviceArgs install -r $Apk
if ($LASTEXITCODE -ne 0) { throw 'Ошибка установки APK' }
& $Adb @deviceArgs shell am start -W -n 'com.dynorixz.buttonscontrol/.MainActivity'
if ($LASTEXITCODE -ne 0) { throw 'Ошибка запуска' }
Write-Output 'Включите Accessibility вручную на часах и выполните калибровку нижней кнопки.'
