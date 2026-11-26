# Dx Buttons Control

Настоящий Android Studio проект Dynorixz Studio для автономных часов Wear OS на Android 11+ (API 30+). Package: `com.dynorixz.buttonscontrol`. Kotlin, Compose for Wear OS Material 3, MVVM, DataStore. Системно управляемый AccessibilityService работает после закрытия интерфейса. Нет INTERNET, root, бесконечного опроса, foreground service или wake lock.

**Три жеста зависят от фактических событий и метаданных окон конкретной прошивки.** Проект не обещает универсальное переназначение Samsung Back. В версии 1.0.1 удержание запускается одноразовым таймером после аппаратного DOWN, даже без повторов. DOWN/UP по умолчанию проходят системе. Можно отключить «Запускать во время удержания», чтобы подтверждать жест повтором либо отпусканием и избежать ложного удержания при потерянном UP. Глобальное удержание не требует доступного дерева или калибровки циферблата. Двойной и тройной жесты требуют откалиброванной сигнатуры и при сомнениях отключены. Подробности: [COMPATIBILITY.md](docs/COMPATIBILITY.md).

## Сборка

Откройте **эту папку** `DxButtonsControl` в Android Studio Panda 3 (2025.3.3 Patch 1) или новее, установите JDK 17, Android SDK Platform 37.0 (`platforms;android-37.0`) и Build Tools 36.0.0. SDK путь задаётся локальным `local.properties`, который не включается в переносимый архив. Gradle Wrapper 9.3.1 включён с SHA-256 проверки distribution. AGP 9.1.1 со встроенным Kotlin, Kotlin/Compose compiler 2.2.10, Wear Material 3 **1.7.1**; зависимости зафиксированы в Gradle Kotlin DSL. compile SDK 37, target SDK 36, min SDK 30.

```powershell
.\scripts\Build.ps1 -JavaPath 'E:\Editors\AndroidToolchain\OpenJDK' -SdkPath 'E:\Editors\AndroidToolchain\SDK'
```

Или после настройки JAVA_HOME и Android SDK:

```powershell
.\gradlew.bat :core:test :app:assembleDebug :app:lintDebug --console=plain
```

На Linux/macOS: `chmod +x gradlew` и `./gradlew :core:test :app:assembleDebug :app:lintDebug`.
Debug APK: `app/build/outputs/apk/debug/app-debug.apk`. Release требует вашей signing configuration; debug signing не используется для публикации. Фактически выполненные проверки перечислены в [VERIFICATION.md](VERIFICATION.md).

## Установка на часы через беспроводной ADB

Включите параметры разработчика на часах и «Беспроводная отладка». Часы и компьютер должны находиться в одной сети. На экране сопряжения часы показывают отдельные IP/порт и код; порт подключения может отличаться от порта сопряжения.

```powershell
adb pair <IP>:<ПОРТ_СОПРЯЖЕНИЯ>
adb connect <IP>:<ПОРТ_ПОДКЛЮЧЕНИЯ>
adb devices -l
adb install -r .\app\build\outputs\apk\debug\app-debug.apk
adb shell am start -W -n com.dynorixz.buttonscontrol/.MainActivity
```

Вместо последней пары команд можно вызвать `scripts/Install.ps1 -Adb <путь-к-adb.exe> -Serial <IP:PORT>`. Установка не включает Accessibility автоматически. Вручную включите «Dx · Управление нижней кнопкой» в специальных возможностях, следуя мастеру. Если система блокирует сторонний APK ограниченными настройками, используйте предоставленный ею экран разрешения ограниченных настроек; приложение не обходит эту защиту.

После установки ADB можно отключить; приложение продолжает работать без соединения. При выключенной службе обработка невозможна, UI показывает реальное состояние. Android обычно восстанавливает вручную включённую службу после загрузки; отдельный BootReceiver её включить не может и не нужен.

## Первый запуск

Семь шагов: приветствие, принцип и приватность, включение службы, пара DOWN/UP нижней кнопки, время жестов и сигнатура циферблата, первые назначения, проверка на часах. Можно выйти из мастера, чтобы изучить диагностику без включения действий.

На экране «Определить кнопку» начните проверку, нажмите и отпустите именно нижнюю кнопку за 12 секунд, затем вернитесь в Dx и подтвердите её. Home/Power не назначаются. Для Samsung допускается `STEM_PRIMARY / 264` только от отдельного аппаратного устройства `pmic_resin` со scan 580 или 114 и непустым descriptor; перехват для него запрещён. Это учитывает реальную раскладку исследованного SM-L355F, где отдельная верхняя Power поступает с `pmic_pwrkey`. Системное подавление этим не подтверждается.

Для первого испытания выберите «Проверить удержание» → «Назначить фонарик», откройте включённый и разблокированный циферблат и удерживайте нижнюю кнопку 1–2 секунды. Через 650 мс должен открыться белый экран. Нажмите его для закрытия. Калибровка циферблата для этого теста не нужна. Любое другое действие затем можно назначить в карточке «Удержание».

Для двойного/тройного запустите 7-секундный захват циферблата и проверьте отрицательные экраны по [DEVICE_TEST_PLAN.md](docs/DEVICE_TEST_PLAN.md).

После изменения циферблата может понадобиться новая сигнатура. Внутри самого Dx индикатор циферблата должен быть отрицательным: приложение является отдельным экраном.

## Действия и интерфейс

- Три карточки жестов с независимым действием, переключателем, таймингом, вибрацией, прямой проверкой команды и сбросом.
- Каталог шести категорий. Приложения с иконками, поиском, избранными и 12 недавними выборами; disabled / private / service-only пакеты не запускаются фиктивно.
- Публичные системные navigation actions и Intent проверяются перед исполнением. Wi-Fi/Bluetooth открывают настройки. Стандартный Intent таймера/будильника/календаря/диктофона/помощника доступен только при установленном обработчике. Секундомер и поиск телефона выбираются как приложения.
- MediaSession с добровольным NotificationListener access; без чтения уведомлений, паролей, песен или переписок. Плеер должен публиковать сессию на часах.
- DX Quick Actions: 4–6 круглых кнопок, мягкая анимация выделения, touch и rotary выбор; нажатие центр/Enter выполняет выделенную команду. Запускается короткоживущая Activity по требованию.
- Экранный фонарик: белый экран с максимальной яркостью только своего Window; закрытие нажатием или Back.
- Исключения глобального удержания, локальная диагностика в памяти (80 записей), JSON, сброс и сведения о приложении.
- Dynamic Color Wear Material 3 при доступности платформы, иначе фирменная палитра purple/blue/lavender на тёмных поверхностях.

«Проверить действие» исполняет реальную команду напрямую, но не является аппаратной проверкой жеста. Сообщение «запрошен запуск» означает успешную передачу Intent, а не подтверждение завершения внешнего приложения. Последовательности ограничены 6 командами с предварительной проверкой доступности и остановкой при ошибке.

## JSON

Основное хранилище — DataStore Preferences со строго проверяемым сериализованным объектом схемы 1. Экспорт и импорт доступны через системный Storage Access Framework. На часах без файлового провайдера есть «Редактор JSON»: локальный экспорт файла, импорт подготовленного файла и текстовый редактор. Любой импорт сбрасывает аппаратную привязку, сигнатуру циферблата и режим перехвата; они должны быть подтверждены на текущем устройстве.

Для debug APK, после нажатия «Сохранить локальный JSON»:

```powershell
python .\scripts\settings_transfer.py export .\dx-settings.json --adb 'E:\Editors\AndroidToolchain\SDK\platform-tools\adb.exe'
python .\scripts\settings_transfer.py import .\dx-settings.json --adb 'E:\Editors\AndroidToolchain\SDK\platform-tools\adb.exe'
```

После передачи импорта нажмите «Импорт из локального файла» в приложении. `--serial` выбирает часы при нескольких устройствах. Скрипт не меняет Accessibility или защищённые системные настройки. Release APK не поддерживает debug `run-as`; там применяются SAF или текстовый JSON редактор.

## Структура

```text
DxButtonsControl/
├── settings.gradle.kts, build.gradle.kts, gradle.properties
├── gradlew, gradlew.bat, gradle/wrapper/
├── app/
│   ├── build.gradle.kts, proguard-rules.pro
│   └── src/main/
│       ├── AndroidManifest.xml
│       ├── res/{drawable,values,xml}/
│       └── java/com/dynorixz/buttonscontrol/
│           ├── DxApplication.kt, MainActivity.kt
│           ├── DxButtonAccessibilityService.kt
│           ├── ButtonEventProcessor.kt, WatchFaceStateDetector.kt
│           ├── AndroidActionPlatform.kt, InstalledAppsRepository.kt
│           ├── SettingsRepository.kt, DiagnosticsRepository.kt
│           ├── CompatibilityChecker.kt, MainViewModel.kt
│           ├── QuickActionsActivity.kt, FlashlightActivity.kt, MediaControlsActivity.kt
│           └── ui/{DxTheme,Components,DxApp}.kt
├── core/
│   ├── build.gradle.kts
│   └── src/{main,test}/kotlin/com/dynorixz/buttonscontrol/core/
│       ├── Models.kt, GestureStateMachine.kt, Actions.kt
│       ├── ApplicationAvailability.kt, WatchFacePolicy.kt, KeySequenceOwnership.kt
│       └── unit tests (test source set)
├── docs/{COMPATIBILITY,DEVICE_TEST_PLAN}.md
├── scripts/{Build.ps1,Install.ps1,settings_transfer.py}
└── VERIFICATION.md
```

`ActionRegistry` описывает каталог и проверку конфигурации, `ActionExecutor` в JVM core отвечает за исполнение через `ActionPlatform` и последовательности; Android platform adapter выполняет настоящие API вызовы. Добавление команды не требует менять обработчик кнопок. `GestureSettingsViewModel` находится рядом с `MainViewModel`, но отвечает только за настройку одного жеста. Все таймеры — одноразовые Handler deadlines; распознаватель использует eventTime/downTime uptime, не календарное время. `KeySequenceOwnership` сохраняет решение фильтрации для пары DOWN/UP независимо от изменения настроек; история потерянных UP ограничена 32 парами.

## Проверки и приватность

JVM unit-тесты покрывают удержание, двойное/тройное, интервалы и bounce, 4+ клика, отмены, повторы, потерянные события, другой экран и окно, перехват/наблюдение, ActionExecutor, доступность приложения, диагностику, сигнатуры циферблата и настройки. Они не заменяют реальных Galaxy Watch. До физических испытаний нельзя объявлять три жеста гарантированно работоспособными на Galaxy Watch 9.

Настройки и идентификаторы приложений остаются локально. Нет сети, аналитики и серверов. Доступность Accessibility не даёт права читать личный контент: код не обращается к node.text, contentDescription, event.text, event.source или notification extras. Единственная работа с деревом — ограниченные метаданные для экрана. Доступ к полному списку пакетов используется только для каталога действий.
