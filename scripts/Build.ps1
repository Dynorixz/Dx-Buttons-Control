param([string]$JavaPath = $env:JAVA_HOME, [string]$SdkPath = $env:ANDROID_SDK_ROOT)
$ErrorActionPreference = 'Stop'
if (-not $JavaPath -and (Test-Path -LiteralPath 'E:\Editors\AndroidToolchain\OpenJDK')) { $JavaPath = 'E:\Editors\AndroidToolchain\OpenJDK' }
if (-not $JavaPath -and (Test-Path -LiteralPath 'D:\Editors\AndroidToolchain\OpenJDK')) { $JavaPath = 'D:\Editors\AndroidToolchain\OpenJDK' }
if (-not $SdkPath) { $SdkPath = $env:ANDROID_HOME }
if (-not $SdkPath -and (Test-Path -LiteralPath 'E:\Editors\AndroidToolchain\SDK')) { $SdkPath = 'E:\Editors\AndroidToolchain\SDK' }
if (-not $SdkPath -and (Test-Path -LiteralPath 'D:\Editors\AndroidToolchain\SDK')) { $SdkPath = 'D:\Editors\AndroidToolchain\SDK' }
if (-not $JavaPath -or -not (Test-Path -LiteralPath (Join-Path $JavaPath 'bin/java.exe'))) { throw 'Укажите JDK 17: -JavaPath' }
if (-not $SdkPath -or -not (Test-Path -LiteralPath (Join-Path $SdkPath 'platforms/android-37.0/android.jar'))) { throw 'Укажите Android SDK с Platform 37.0: -SdkPath' }
$env:JAVA_HOME = $JavaPath
$env:ANDROID_SDK_ROOT = $SdkPath
$projectRoot = Split-Path -Parent $PSScriptRoot
Push-Location -LiteralPath $projectRoot
try {
    $sdkProperty = $SdkPath.Replace('\', '/').Replace(':', '\:')
    [IO.File]::WriteAllText((Join-Path $projectRoot 'local.properties'), "sdk.dir=$sdkProperty`n", [Text.UTF8Encoding]::new($false))
    & '.\gradlew.bat' :core:test :app:assembleDebug :app:lintDebug --console=plain
    if ($LASTEXITCODE -ne 0) { throw "Сборка завершилась с кодом $LASTEXITCODE" }
} finally { Pop-Location }
