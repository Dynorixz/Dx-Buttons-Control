package com.dynorixz.buttonscontrol

import android.accessibilityservice.AccessibilityService
import android.app.NotificationManager
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.media.session.MediaController
import android.media.session.MediaSessionManager
import android.media.session.PlaybackState
import android.net.Uri
import android.os.Build
import android.os.VibrationEffect
import android.os.Vibrator
import android.provider.AlarmClock
import android.provider.Settings
import android.service.notification.NotificationListenerService
import android.widget.Toast
import androidx.core.content.edit
import com.dynorixz.buttonscontrol.core.*
import kotlinx.coroutines.delay

class MediaAccessService : NotificationListenerService() {
    // No notification callbacks, text, extras, icons or metadata are inspected or stored.
}

class AndroidActionPlatform(private val context: Context, private val apps: InstalledAppsRepository, private val service: AccessibilityService? = null) : ActionPlatform {
    private val pm = context.packageManager
    private val notificationManager = context.getSystemService(NotificationManager::class.java)
    private val mediaComponent = ComponentName(context, MediaAccessService::class.java)
    private val appActions = setOf("app", "music_app", "stopwatch", "find_phone")
    private val global = mapOf("home" to AccessibilityService.GLOBAL_ACTION_HOME, "back" to AccessibilityService.GLOBAL_ACTION_BACK,
        "recents" to AccessibilityService.GLOBAL_ACTION_RECENTS, "notifications" to AccessibilityService.GLOBAL_ACTION_NOTIFICATIONS,
        "quick_settings" to AccessibilityService.GLOBAL_ACTION_QUICK_SETTINGS, "lock" to AccessibilityService.GLOBAL_ACTION_LOCK_SCREEN).let {
        if (Build.VERSION.SDK_INT >= 31) it + ("all_apps" to AccessibilityService.GLOBAL_ACTION_ACCESSIBILITY_ALL_APPS) else it
    }
    private val intentActions = mapOf("settings" to Settings.ACTION_SETTINGS, "wifi" to Settings.ACTION_WIFI_SETTINGS,
        "bluetooth" to Settings.ACTION_BLUETOOTH_SETTINGS, "sound" to Settings.ACTION_SOUND_SETTINGS,
        "display" to Settings.ACTION_DISPLAY_SETTINGS, "timer" to AlarmClock.ACTION_SET_TIMER,
        "alarm" to AlarmClock.ACTION_SHOW_ALARMS, "calendar" to Intent.ACTION_MAIN,
        "recorder" to "android.provider.MediaStore.RECORD_SOUND", "assistant" to Intent.ACTION_VOICE_COMMAND)
    private val allowedSystem = setOf(Settings.ACTION_SETTINGS, Settings.ACTION_WIFI_SETTINGS, Settings.ACTION_BLUETOOTH_SETTINGS,
        Settings.ACTION_SOUND_SETTINGS, Settings.ACTION_DISPLAY_SETTINGS, Settings.ACTION_ACCESSIBILITY_SETTINGS,
        Settings.ACTION_NOTIFICATION_POLICY_ACCESS_SETTINGS, Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS,
        Settings.ACTION_BATTERY_SAVER_SETTINGS, Settings.ACTION_DATE_SETTINGS, Settings.ACTION_LOCALE_SETTINGS)

    override fun availability(action: Action): ActionResult {
        if (action.id == "all_apps" && Build.VERSION.SDK_INT < 31) return ActionResult(false, "Открытие списка приложений требует API 31+ и опубликованного systemAction")
        if (action.id in appActions) return apps.availability(action.argument)
        global[action.id]?.let { id ->
            if (service == null) return ActionResult(false, "Включите службу специальных возможностей")
            return ActionResult(service.systemActions.any { it.id == id }, if (service.systemActions.any { it.id == id }) "Доступно" else "Система не предоставляет это глобальное действие")
        }
        return when(action.id) {
            "play_pause", "next", "previous" -> {
                if (!mediaGranted()) ActionResult(false, "Разрешите доступ Dx к MediaSession в настройках доступа к уведомлениям")
                else if (sessions().isEmpty()) ActionResult(false, "Нет активной MediaSession на часах") else ActionResult(true, "Доступно")
            }
            "brightness" -> ActionResult(Settings.System.canWrite(context), "${if (Settings.System.canWrite(context)) "Доступно" else "Разрешите изменение системных настроек или откройте настройки экрана"}")
            "dnd" -> ActionResult(notificationManager.isNotificationPolicyAccessGranted, if (notificationManager.isNotificationPolicyAccessGranted) "Доступно" else "Предоставьте доступ к Не беспокоить")
            "vibrate" -> ActionResult(context.getSystemService(Vibrator::class.java).hasVibrator(), "Вибромотор ${if(context.getSystemService(Vibrator::class.java).hasVibrator()) "доступен" else "недоступен"}")
            "shortcut", "system_intent" -> {
                val intent = safeIntent(action) ?: return ActionResult(false, "Intent не разрешён или нет доступной Activity")
                canLaunch(intent)
            }
            in intentActions.keys -> canLaunch(intentFor(action))
            "none", "toast", "quick_actions", "flashlight", "media_controls" -> ActionResult(true, "Доступно")
            else -> ActionResult(false, "Действие не зарегистрировано")
        }
    }
    override suspend fun execute(action: Action): ActionResult {
        if (action.id in appActions) return apps.launch(action.argument)
        global[action.id]?.let { id ->
            val success = service?.performGlobalAction(id) == true
            return ActionResult(success, if (success) "Система приняла команду" else "Система отклонила команду")
        }
        return when(action.id) {
            "none" -> ActionResult(true, "Действие не назначено")
            "toast" -> { Toast.makeText(context, action.argument.ifBlank { "Dx Buttons Control" }.take(120), Toast.LENGTH_SHORT).show(); ActionResult(true, "Сообщение показано") }
            "vibrate" -> { vibrate(); ActionResult(true, "Вибрация выполнена") }
            "play_pause", "next", "previous" -> media(action.id)
            "brightness" -> {
                val value = action.argument.toIntOrNull()?.coerceIn(1, 255) ?: return ActionResult(false, "Укажите яркость 1–255")
                val ok = Settings.System.putInt(context.contentResolver, Settings.System.SCREEN_BRIGHTNESS_MODE, Settings.System.SCREEN_BRIGHTNESS_MODE_MANUAL) &&
                    Settings.System.putInt(context.contentResolver, Settings.System.SCREEN_BRIGHTNESS, value)
                ActionResult(ok, if (ok) "Ручная яркость $value записана; прошивка может переопределить её" else "Прошивка отклонила изменение яркости")
            }
            "dnd" -> {
                // On Android 15+ this API operates the app's implicit Zen rule, not every global rule.
                val prefs = context.getSharedPreferences("dx_zen", Context.MODE_PRIVATE)
                val enable = if (Build.VERSION.SDK_INT >= 35) !prefs.getBoolean("enabled", false) else notificationManager.currentInterruptionFilter == NotificationManager.INTERRUPTION_FILTER_ALL
                notificationManager.setInterruptionFilter(if (enable) NotificationManager.INTERRUPTION_FILTER_PRIORITY else NotificationManager.INTERRUPTION_FILTER_ALL)
                prefs.edit { putBoolean("enabled", enable) }
                ActionResult(true, if (Build.VERSION.SDK_INT >= 35) "Правило Не беспокоить Dx ${if(enable) "включено" else "выключено"}; другие правила остаются активными" else "Команда Не беспокоить отправлена")
            }
            "quick_actions" -> launch(Intent(context, QuickActionsActivity::class.java))
            "flashlight" -> launch(Intent(context, FlashlightActivity::class.java))
            "media_controls" -> launch(Intent(context, MediaControlsActivity::class.java))
            "shortcut", "system_intent" -> safeIntent(action)?.let(::launch) ?: ActionResult(false, "Intent недоступен")
            in intentActions.keys -> launch(intentFor(action))
            else -> ActionResult(false, "Нет обработчика действия")
        }
    }
    override suspend fun pause(milliseconds: Long) = delay(milliseconds)
    fun vibrate() { if (context.getSystemService(Vibrator::class.java).hasVibrator()) context.getSystemService(Vibrator::class.java).vibrate(VibrationEffect.createOneShot(35, VibrationEffect.DEFAULT_AMPLITUDE)) }
    fun mediaGranted() = notificationManager.isNotificationListenerAccessGranted(mediaComponent)
    fun sessions(): List<MediaController> = if (mediaGranted()) runCatching { context.getSystemService(MediaSessionManager::class.java).getActiveSessions(mediaComponent) }.getOrDefault(emptyList()) else emptyList()
    private fun media(id: String): ActionResult {
        val controllers = sessions()
        val controller = controllers.firstOrNull { it.playbackState?.state == PlaybackState.STATE_PLAYING } ?: controllers.firstOrNull()
            ?: return ActionResult(false, "Активная MediaSession недоступна")
        val state = controller.playbackState
        val controls = controller.transportControls
        val required = when(id) { "next" -> PlaybackState.ACTION_SKIP_TO_NEXT; "previous" -> PlaybackState.ACTION_SKIP_TO_PREVIOUS; else -> if (state?.state == PlaybackState.STATE_PLAYING) PlaybackState.ACTION_PAUSE else PlaybackState.ACTION_PLAY }
        val actions = state?.actions ?: 0L
        val toggle = id == "play_pause" && actions and PlaybackState.ACTION_PLAY_PAUSE != 0L
        if (actions and required == 0L && !toggle) return ActionResult(false, "Плеер не поддерживает эту команду")
        when(id) { "next" -> controls.skipToNext(); "previous" -> controls.skipToPrevious(); else -> if (state?.state == PlaybackState.STATE_PLAYING) controls.pause() else controls.play() }
        return ActionResult(true, "Команда отправлена MediaSession ${controller.packageName}")
    }
    private fun intentFor(action: Action) = Intent(intentActions.getValue(action.id)).apply {
        if (action.id == "calendar") addCategory(Intent.CATEGORY_APP_CALENDAR)
        if (action.id == "timer") putExtra(AlarmClock.EXTRA_SKIP_UI, false)
    }
    private fun safeIntent(action: Action): Intent? = runCatching {
        if (action.id == "system_intent") {
            if (action.argument !in allowedSystem) return null
            return Intent(action.argument)
        }
        val parsed = Intent.parseUri(action.argument, 0)
        if (parsed.action !in (allowedSystem + setOf(Intent.ACTION_VIEW, Intent.ACTION_MAIN, Intent.ACTION_ASSIST, Intent.ACTION_VOICE_COMMAND))) return null
        parsed.flags = Intent.FLAG_ACTIVITY_NEW_TASK
        parsed.selector = null; parsed.clipData = null
        if (parsed.data?.scheme in setOf("file", "javascript")) return null
        parsed
    }.getOrNull()
    @Suppress("DEPRECATION")
    private fun canLaunch(intent: Intent): ActionResult {
        val info = pm.resolveActivity(intent, PackageManager.MATCH_DEFAULT_ONLY)?.activityInfo ?: return ActionResult(false, "На часах нет обработчика этого Intent. Выберите приложение или другой системный экран.")
        val allowed = info.enabled && info.applicationInfo.enabled && (info.exported || info.packageName == context.packageName) &&
            (info.permission == null || context.checkSelfPermission(info.permission) == PackageManager.PERMISSION_GRANTED)
        return ActionResult(allowed, if (allowed) "Доступно" else "Activity защищена разрешением")
    }
    private fun launch(intent: Intent): ActionResult = try {
        val available = canLaunch(intent)
        if (!available.success) available else { context.startActivity(intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)); ActionResult(true, "Запрошено открытие экрана") }
    } catch (_: android.content.ActivityNotFoundException) { ActionResult(false, "Обработчик больше недоступен") }
    catch (_: SecurityException) { ActionResult(false, "Запуск запрещён системой") }
}
