package com.dynorixz.buttonscontrol.core

enum class ActionCategory(val title: String) { APPS("Приложения"), NAVIGATION("Навигация"), TOOLS("Инструменты"), MUSIC("Музыка"), SYSTEM("Система"), SPECIAL("Специальные") }
data class ActionSpec(val id: String, val title: String, val category: ActionCategory, val icon: String, val argumentHint: String = "")
data class ActionResult(val success: Boolean, val message: String)

object ActionRegistry {
    val specs = listOf(
        ActionSpec("app", "Запустить приложение", ActionCategory.APPS, "apps"),
        ActionSpec("home", "Главный циферблат", ActionCategory.NAVIGATION, "home"),
        ActionSpec("all_apps", "Список приложений", ActionCategory.NAVIGATION, "apps"),
        ActionSpec("recents", "Недавние приложения", ActionCategory.NAVIGATION, "apps"),
        ActionSpec("notifications", "Уведомления", ActionCategory.NAVIGATION, "notifications"),
        ActionSpec("quick_settings", "Быстрые настройки", ActionCategory.NAVIGATION, "settings"),
        ActionSpec("back", "Назад", ActionCategory.NAVIGATION, "back"),
        ActionSpec("settings", "Настройки часов", ActionCategory.SYSTEM, "settings"),
        ActionSpec("wifi", "Настройки Wi-Fi", ActionCategory.SYSTEM, "wifi"),
        ActionSpec("bluetooth", "Настройки Bluetooth", ActionCategory.SYSTEM, "bluetooth"),
        ActionSpec("sound", "Настройки звука", ActionCategory.SYSTEM, "volume"),
        ActionSpec("display", "Настройки экрана", ActionCategory.SYSTEM, "sun"),
        ActionSpec("brightness", "Установить яркость", ActionCategory.SYSTEM, "sun", "1–255; требуется доступ к системным настройкам"),
        ActionSpec("dnd", "Переключить Не беспокоить", ActionCategory.SYSTEM, "moon"),
        ActionSpec("lock", "Заблокировать экран", ActionCategory.SYSTEM, "lock"),
        ActionSpec("system_intent", "Системный экран", ActionCategory.SYSTEM, "settings", "Поддерживаемый action android.settings.*"),
        ActionSpec("timer", "Таймер", ActionCategory.TOOLS, "timer"),
        ActionSpec("stopwatch", "Секундомер: выбрать приложение", ActionCategory.TOOLS, "timer", "Идентификатор пакета приложения секундомера"),
        ActionSpec("alarm", "Будильники", ActionCategory.TOOLS, "alarm"),
        ActionSpec("calendar", "Календарь", ActionCategory.TOOLS, "calendar"),
        ActionSpec("recorder", "Диктофон", ActionCategory.TOOLS, "mic"),
        ActionSpec("assistant", "Голосовой помощник", ActionCategory.TOOLS, "mic"),
        ActionSpec("find_phone", "Поиск телефона: выбрать приложение", ActionCategory.TOOLS, "phone", "Идентификатор пакета поиска телефона"),
        ActionSpec("flashlight", "Экранный фонарик", ActionCategory.TOOLS, "sun"),
        ActionSpec("shortcut", "Пользовательский ярлык", ActionCategory.TOOLS, "bolt", "URI Intent: только доступная Activity"),
        ActionSpec("play_pause", "Play / Pause", ActionCategory.MUSIC, "play"),
        ActionSpec("next", "Следующий трек", ActionCategory.MUSIC, "next"),
        ActionSpec("previous", "Предыдущий трек", ActionCategory.MUSIC, "previous"),
        ActionSpec("music_app", "Музыкальное приложение", ActionCategory.MUSIC, "play", "Идентификатор пакета"),
        ActionSpec("media_controls", "Управление музыкой", ActionCategory.MUSIC, "play"),
        ActionSpec("none", "Ничего не делать", ActionCategory.SPECIAL, "block"),
        ActionSpec("toast", "Короткое сообщение", ActionCategory.SPECIAL, "notifications", "Текст сообщения"),
        ActionSpec("vibrate", "Вибрация", ActionCategory.SPECIAL, "vibration"),
        ActionSpec("quick_actions", "DX Quick Actions", ActionCategory.SPECIAL, "bolt"),
        ActionSpec("sequence", "Последовательность действий", ActionCategory.SPECIAL, "bolt")
    )
    fun spec(id: String) = specs.firstOrNull { it.id == id }
    fun title(action: Action) = spec(action.id)?.title ?: "Неизвестное действие"
    fun validate(action: Action, depth: Int = 0) {
        require(depth <= 2 && spec(action.id) != null && action.argument.length <= 4096 && action.steps.size <= 6)
        if (action.id == "sequence") { require(action.steps.isNotEmpty()); action.steps.forEach { validate(it, depth + 1) } }
        else require(action.steps.isEmpty())
        if (action.id == "brightness") require(action.argument.toIntOrNull() in 1..255)
    }
}

interface ActionPlatform {
    fun availability(action: Action): ActionResult
    suspend fun execute(action: Action): ActionResult
    suspend fun pause(milliseconds: Long)
}

class ActionExecutor(private val platform: ActionPlatform) {
    suspend fun execute(action: Action): ActionResult {
        try { ActionRegistry.validate(action) } catch (_: IllegalArgumentException) { return ActionResult(false, "Некорректное действие") }
        return try {
            if (action.id == "sequence") {
                // Validate availability of the entire sequence before its first side effect.
                val unavailable = leaves(action).map { platform.availability(it) }.firstOrNull { !it.success }
                if (unavailable != null) unavailable else {
                    for ((index, step) in action.steps.withIndex()) {
                        val result = execute(step)
                        if (!result.success) return result
                        if (index < action.steps.lastIndex) platform.pause(180)
                    }
                    ActionResult(true, "Последовательность выполнена")
                }
            } else {
                val available = platform.availability(action)
                if (available.success) platform.execute(action) else available
            }
        } catch (e: kotlinx.coroutines.CancellationException) { throw e }
        catch (_: SecurityException) { ActionResult(false, "Нет необходимого разрешения") }
        catch (_: Exception) { ActionResult(false, "Не удалось выполнить действие") }
    }
    private fun leaves(action: Action): List<Action> = if (action.id == "sequence") action.steps.flatMap(::leaves) else listOf(action)
}
