<div align="center">

![Dx Buttons Control — Dynorixz Studio](docs/assets/hero.svg)

**Три жеста нижней кнопки. Ваши действия на часах.**

[![Wear OS](https://img.shields.io/badge/Wear_OS-Android_11%2B-8A68FF?style=flat-square)](docs/COMPATIBILITY.md)
[![Kotlin](https://img.shields.io/badge/Kotlin-2.2.10-B9AEFF?style=flat-square)](docs/BUILDING.md)
[![Android CI](https://github.com/Dynorixz/Dx-Buttons-Control/actions/workflows/android.yml/badge.svg)](https://github.com/Dynorixz/Dx-Buttons-Control/actions/workflows/android.yml)
[![Release](https://img.shields.io/github/v/release/Dynorixz/Dx-Buttons-Control?include_prereleases&style=flat-square&color=4F7CFF)](https://github.com/Dynorixz/Dx-Buttons-Control/releases)

[**Выбрать APK для своих часов**](docs/DOWNLOADS.md) · [Установка](docs/INSTALLATION.md) · [Совместимость](docs/COMPATIBILITY.md) · [English](README.en.md)

</div>

## Управление, которое всегда под рукой

**Dx Buttons Control** — приложение Dynorixz Studio для автономных часов Wear OS. Назначайте нижней физической кнопке Back запуск приложений, системные команды, музыку или собственное меню DX Quick Actions. Интерфейс рассчитан на круглый экран; настройки хранятся на часах.

| Жест | Где работает | Поведение |
|---|---|---|
| **Удержание** | На включённом, разблокированном экране, где прошивка передаёт события | Одно действие после порога; по умолчанию 650 мс |
| **Двойное** | Только на подтверждённом циферблате | Ожидает третьего нажатия; интервал по умолчанию 350 мс |
| **Тройное** | Только на подтверждённом циферблате | Выполняет тройное действие, отменяя отложенное двойное |

Обычное короткое нажатие в режиме наблюдения сохраняет поведение Samsung. Root, Shizuku, Xposed и постоянное подключение ADB не требуются.

| Для каких часов | Скачать |
|---|---|
| SM-L355F или часы с подходящим существующим профилем кнопки | [**1.1.0 — базовая версия**](https://github.com/Dynorixz/Dx-Buttons-Control/releases/tag/v1.1.0) |
| Galaxy Watch Ultra SM-L705 | [1.1.2-ultra-test](https://github.com/Dynorixz/Dx-Buttons-Control/releases/tag/v1.1.2-ultra-test) |
| Galaxy Watch6 Classic SM-R960 | [1.1.3](https://github.com/Dynorixz/Dx-Buttons-Control/releases/tag/v1.1.3) |
| Galaxy Watch8 Classic SM-L505F | [1.1.5](https://github.com/Dynorixz/Dx-Buttons-Control/releases/tag/v1.1.5) |

[Таблица с проверками, диагностикой и остальными версиями](docs/DOWNLOADS.md).

## Интерфейс

<div align="center">
<img src="docs/assets/app-home.png" alt="Главный экран с карточками жестов" width="240" />
&nbsp;&nbsp;
<img src="docs/assets/app-about.png" alt="О приложении: Dynorixz Studio и ссылки автора" width="240" />
</div>

Реальные снимки интерфейса версии 1.1.0 с Samsung SM-L355F. В 1.1.2 обновлены аппаратный профиль и диагностика; эти изображения не подтверждают испытаний на Ultra.

## Возможности

- **Приложения:** поиск, иконки, избранное, 12 недавних выборов, проверка доступной Activity перед запуском.
- **Навигация:** циферблат, Back, уведомления, быстрые настройки, недавние и список приложений — если прошивка предоставляет системное действие.
- **Инструменты:** таймер, будильник, календарь, диктофон, помощник, экранный фонарик и разрешённые ярлыки. Секундомер и поиск телефона выбираются как приложения.
- **Музыка:** Play/Pause, следующий и предыдущий трек через MediaSession; отдельный экран управления.
- **Система:** экраны Wi-Fi, Bluetooth, звука и дисплея; яркость и DND при выданном доступе. Защищённые сетевые переключатели напрямую не меняются.
- **DX Quick Actions:** 4–6 закреплённых команд, крупные круглые кнопки, touch и вращение рамки/rotary.
- **Персонализация:** отдельное действие и вибрация для каждого жеста, интервалы, исключения приложений, JSON экспорт/импорт.
- **Диагностика:** фактические поля InputDevice, причины отклонения, обнаруженный жест, результат команды и локальный JSON отчёт.

## Проверенная совместимость

| Устройство / сценарий | Статус |
|---|---|
| **Samsung SM-L355F**, `pmic_resin`, 264 / scan 580 | Три жеста проверены в debug 1.1.0. Подписанный release отдельно собран и проверен подписью |
| **Galaxy Watch Ultra SM-L705**, `gpio_keys`, 264 / scan 580 | Пользователь подтвердил работу сборки 1.1.2-ultra-test на часах клиента |
| **Galaxy Watch6 Classic SM-R960** | Профиль добавлен в 1.1.3. Аппаратная проверка жестов ещё нужна |
| **Galaxy Watch8 Classic SM-L505F** | События нижней кнопки подтверждены отчётом клиента. Профиль добавлен в 1.1.5, тест жестов на этих часах ещё нужен |
| Другие Wear OS часы | Условная совместимость: нужна доставка событий, успешная калибровка и подтверждение циферблата |
| AOD, выключенный экран, блокировка | Обработка отключена |

GPIO-профили требуют совпадения модели, источника, кода и scanCode. Также проверяются непустой descriptor, `isVirtual=false` и согласованная пара DOWN/UP. Home/Power защищены; правило `pmic_resin` сохранено.

Wallet/Pay и тренировки могут использовать ту же кнопку. Dx не отменяет закрытую политику Samsung. В режиме наблюдения системное действие может выполниться одновременно; если оно уводит с циферблата, многократный жест отменяется. [Ограничения и публичные API →](docs/COMPATIBILITY.md)

## Быстрый старт

1. Скачайте **APK для своей модели** из [таблицы версий](docs/DOWNLOADS.md) и установите на часы. APK для Wear OS, не для телефона.
2. Включите **«Dx · Управление нижней кнопкой»** в системных специальных возможностях.
3. В Dx откройте **Настройки → Кнопка и циферблат → Определить кнопку**. Нажмите и отпустите нижнюю кнопку, вернитесь в Dx и подтвердите **«Это нижняя кнопка»**.
4. Назначьте удержанию вибрацию и проверьте физическое нажатие. Для двойного/тройного сначала настройте циферблат и убедитесь, что он определён.
5. При отказе откройте **«Отчёт кнопки»**. Экранная проверка действия проверяет команду, а не физический жест.

[Установка по ADB, обновление и устранение проблем →](docs/INSTALLATION.md)

## Сборка и устройство проекта

Полный Android Studio проект: Kotlin, Compose for Wear OS Material 3, MVVM, DataStore. Минимальный Android — API 30, target — 36, compile SDK — 37.0; JDK 17, Gradle Wrapper 9.3.1, AGP 9.1.1.

```powershell
.\gradlew.bat :core:test :app:assembleDebug :app:lintDebug --max-workers=1 --console=plain
```

На Linux/macOS: `./gradlew :core:test :app:assembleDebug :app:lintDebug --max-workers=1 --console=plain`.

| Модуль | Ответственность |
|---|---|
| `core` | Автомат жестов, профили кнопок, калибровка, политики циферблата, действия и JVM-тесты |
| `app` | AccessibilityService, Android API, InputDevice/окна, Compose, DataStore и диагностика |
| `scripts` | Сборка, подпись release, установка и debug-перенос настроек |

[Сборка и подпись →](docs/BUILDING.md) · [Архитектура →](docs/ARCHITECTURE.md) · [Участие →](CONTRIBUTING.md)

## Проверки и приватность

Локальная проверка 1.1.5: **175 JVM-тестов**, успешные `assembleRelease` и `lintRelease` — 0 ошибок, 16 предупреждений. Подпись v2 и ZIP-выравнивание APK проверены. [Полный отчёт и границы проверки →](VERIFICATION.md)

Accessibility используется для физических кнопок и ограниченных метаданных окон. Приложение не читает тексты переписок, пароли или контакты, не имеет разрешения INTERNET, аналитики, постоянного foreground service или периодического опроса. Настройки и отчёты хранятся локально; экспорт запускает пользователь. [Разрешения и приватность →](docs/PRIVACY.md)

## Документация и обратная связь

[История изменений](CHANGELOG.md) · [План испытаний на часах](docs/DEVICE_TEST_PLAN.md) · [Сообщить о проблеме](https://github.com/Dynorixz/Dx-Buttons-Control/issues/new/choose) · [Безопасность](SECURITY.md)

**Dynorixz Studio** — [Telegram](https://t.me/dynorixz) · [GitHub](https://github.com/Dynorixz) · [Поддержать проект](https://pay.cloudtips.ru/p/5d56061d)

© 2026 Dynorixz Studio. Отдельная лицензия на повторное использование исходников пока не выбрана.
