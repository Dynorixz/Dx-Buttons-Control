# Сборка и подпись

## Требования

| Компонент | Версия проекта |
|---|---|
| JDK | 17 |
| Android SDK Platform | 37.0 (`platforms;android-37.0`) |
| Build Tools | 36.0.0 |
| Gradle Wrapper | 9.3.1, SHA-256 дистрибутива указан в wrapper properties |
| Android Gradle Plugin | 9.1.1, встроенный Kotlin для Android |
| Kotlin / Compose compiler | 2.2.10 |
| Wear Compose Material 3 | 1.7.1 |
| min / target API | 30 / 36 |

Откройте корневую папку в совместимой Android Studio. Укажите SDK через `ANDROID_HOME` / `ANDROID_SDK_ROOT` или локальный `local.properties`, например `sdk.dir=C\:/Android/Sdk`. Этот файл не хранится в Git.

## Debug и проверки

```powershell
.\gradlew.bat :core:test :app:assembleDebug :app:lintDebug --max-workers=1 --console=plain
```

Linux/macOS:

```sh
chmod +x gradlew
./gradlew :core:test :app:assembleDebug :app:lintDebug --max-workers=1 --console=plain
```

Или на Windows передайте пути явно:

```powershell
.\scripts\Build.ps1 -JavaPath 'C:\Android\jdk-17' -SdkPath 'C:\Android\Sdk'
```

APK: `app/build/outputs/apk/debug/app-debug.apk`. JVM-результаты: `core/build/test-results/test/`. Lint: `app/build/reports/`. GitHub Actions выполняет те же проверки; его debug APK не является подписанным студийным release.

## Release

Храните PKCS12/JKS и properties-файл **вне репозитория**. Внешний файл содержит ваши секретные значения полей `storeFile`, `storePassword`, `keyAlias`, `keyPassword`.

```powershell
.\scripts\BuildRelease.ps1 -SigningFile 'C:\Private\dx-release.properties' -JavaPath 'C:\Android\jdk-17' -SdkPath 'C:\Android\Sdk'
```

Эквивалент Gradle:

```sh
./gradlew :core:test :app:assembleRelease :app:lintRelease -PdxReleaseSigningFile=/private/dx-release.properties --max-workers=1 --console=plain
```

Вместо `-P` поддерживается `DX_RELEASE_SIGNING_FILE`. `BuildRelease.ps1` требует signing-файл и проверяет подпись. Обычный `assembleRelease` без него создаёт unsigned APK, который нельзя выдавать за установочный релиз.

Результат: `app/build/outputs/apk/release/app-release.apk`. R8 включён. Android требует совпадения сертификата при обновлении. Сборка с вашим ключом не обновит официальный release поверх.

Публичный SHA-256 сертификата Dynorixz Studio:

```text
7eeee74660625d66253b472c58f63cbe472edb1118ec537057d2962827714a6b
```

Проверки: `apksigner verify --verbose --print-certs <APK>` и `zipalign -c -P 16 -v 4 <APK>` из Build Tools. Размеры и SHA-256 приведены в релизе и SHA256SUMS. Закрытый ключ и пароль автоматически не добавляются в исходники, GitHub Secrets или архив.

## Переносимость

ZIP из Releases содержит оба модуля, Kotlin/Compose, ресурсы, wrapper JAR, Gradle DSL, документацию и скрипты. SDK, JDK, кэши и приватная подпись устанавливаются/настраиваются отдельно. Публичные зависимости закреплены в Gradle; для первой сборки нужен доступ к их репозиториям.
