param(
    [Parameter(Mandatory = $true)][string]$SigningFile,
    [string]$JavaPath = $env:JAVA_HOME,
    [string]$SdkPath = $env:ANDROID_SDK_ROOT
)
$ErrorActionPreference = 'Stop'
if (-not $JavaPath -and (Test-Path -LiteralPath 'E:\Editors\AndroidToolchain\OpenJDK')) {
    $JavaPath = 'E:\Editors\AndroidToolchain\OpenJDK'
}
if (-not $JavaPath -and (Test-Path -LiteralPath 'D:\Editors\AndroidToolchain\OpenJDK')) {
    $JavaPath = 'D:\Editors\AndroidToolchain\OpenJDK'
}
if (-not $SdkPath) { $SdkPath = $env:ANDROID_HOME }
if (-not $SdkPath -and (Test-Path -LiteralPath 'E:\Editors\AndroidToolchain\SDK')) {
    $SdkPath = 'E:\Editors\AndroidToolchain\SDK'
}
if (-not $SdkPath -and (Test-Path -LiteralPath 'D:\Editors\AndroidToolchain\SDK')) {
    $SdkPath = 'D:\Editors\AndroidToolchain\SDK'
}
if (-not $JavaPath -or -not (Test-Path -LiteralPath (Join-Path $JavaPath 'bin\java.exe'))) {
    throw 'Укажите JDK 17: -JavaPath <каталог JDK> или JAVA_HOME'
}
if (-not $SdkPath -or -not (Test-Path -LiteralPath (Join-Path $SdkPath 'platforms\android-37.0\android.jar'))) {
    throw 'Укажите Android SDK с platform 37.0: -SdkPath <каталог SDK>'
}
if (-not (Test-Path -LiteralPath $SigningFile -PathType Leaf)) {
    throw 'Файл release-подписи не найден. Храните его и ключ вне исходников.'
}
$env:JAVA_HOME = $JavaPath
$env:ANDROID_SDK_ROOT = $SdkPath
$signingPath = (Resolve-Path -LiteralPath $SigningFile).Path
$projectRoot = Split-Path -Parent $PSScriptRoot
Push-Location -LiteralPath $projectRoot
try {
    & '.\gradlew.bat' :core:test :app:assembleRelease :app:lintRelease "-PdxReleaseSigningFile=$signingPath" --max-workers=1 --console=plain
    if ($LASTEXITCODE -ne 0) { throw "Сборка завершилась с кодом $LASTEXITCODE" }
    $apk = Join-Path $projectRoot 'app\build\outputs\apk\release\app-release.apk'
    if (-not (Test-Path -LiteralPath $apk -PathType Leaf)) { throw 'Подписанный release APK не создан' }
    & (Join-Path $SdkPath 'build-tools\36.0.0\apksigner.bat') verify --verbose $apk
    if ($LASTEXITCODE -ne 0) { throw 'Подпись release APK не прошла проверку' }
} finally { Pop-Location }
