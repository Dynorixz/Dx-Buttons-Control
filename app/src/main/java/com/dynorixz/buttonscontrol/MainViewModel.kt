package com.dynorixz.buttonscontrol

import android.app.Application
import android.content.Intent
import androidx.core.net.toUri
import android.provider.Settings
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.dynorixz.buttonscontrol.core.*
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File

class MainViewModel(application: Application) : AndroidViewModel(application) {
    private val app = application as DxApplication
    val settings = app.settings.settings
    val diagnostics = app.diagnostics.state
    val storageError = app.settings.error
    val ready = app.settings.ready
    val apps = MutableStateFlow<List<InstalledApp>>(emptyList())
    val appsLoading = MutableStateFlow(false)
    val message = MutableStateFlow("")
    val linkStatus = MutableStateFlow("")
    private val labels = mutableMapOf<String, String>()
    fun actionLabel(action: Action): String = if(action.id in setOf("app", "music_app", "stopwatch", "find_phone")) {
        labels.getOrPut(action.argument) {
            runCatching { app.packageManager.getApplicationLabel(app.packageManager.getApplicationInfo(action.argument, 0)).toString() }.getOrDefault("Приложение удалено")
        }
    } else if(action.id == "none") "Выбрать действие" else ActionRegistry.title(action)
    fun openAuthorLink(url: String) {
        if(url !in AuthorLinks.allowed) return
        linkStatus.value = "Открываем на телефоне…"
        val executor = androidx.core.content.ContextCompat.getMainExecutor(app)
        val helper = androidx.wear.remote.interactions.RemoteActivityHelper(app, executor)
        val intent = Intent(Intent.ACTION_VIEW, url.toUri()).addCategory(Intent.CATEGORY_BROWSABLE)
        runCatching { helper.startRemoteActivity(intent) }.onSuccess { future ->
            future.addListener({
                linkStatus.value = runCatching { future.get() }.fold({ "Открыто на телефоне" }, { "Не удалось открыть. Проверьте подключение телефона." })
            }, executor)
        }.onFailure { linkStatus.value = "Не удалось открыть. Проверьте подключение телефона." }
    }
    fun refresh() {
        val checker = CompatibilityChecker(app)
        app.diagnostics.change { it.copy(enabled = checker.enabled(), otherFilters = checker.competingFilters()) }
    }
    fun loadApps() { viewModelScope.launch { appsLoading.value = true; runCatching { app.apps.list() }.onSuccess { apps.value = it }.onFailure { message.value = "Список приложений недоступен" }; appsLoading.value = false } }
    fun update(block: (DxSettings) -> DxSettings) { viewModelScope.launch { runCatching { app.settings.update(block) }.onFailure { message.value = "Изменения не сохранены: ${it.message}" } } }
    fun test(action: Action) {
        val current = DxButtonAccessibilityService.current()
        if (current != null) current.execute(action)
        else viewModelScope.launch { app.diagnostics.result(ActionExecutor(AndroidActionPlatform(app, app.apps)).execute(action)) }
    }
    fun bindCandidate() {
        diagnostics.value.candidate?.takeIf(ButtonSafety::allowed)?.let { binding ->
            update { it.copy(button = binding, mode = InputMode.OBSERVE) }
        }
    }
    fun setupHoldTest() {
        update { it.copy(hold = GestureSetting(true, Action("flashlight")), holdMs = 650, holdOnTimer = true, mode = InputMode.OBSERVE, runWhenConnected = true) }
        message.value = "Тест назначен: удерживайте нижнюю кнопку на включённом циферблате. Должен открыться белый фонарик."
    }
    fun calibrateButton() { DxButtonAccessibilityService.current()?.calibrateButton() ?: run { message.value = "Служба не подключена" } }
    fun calibrateFace() { DxButtonAccessibilityService.current()?.calibrateFace() ?: run { message.value = "Служба не подключена" } }
    fun permissions(action: String) {
        val intent = Intent(action).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        if (action == Settings.ACTION_MANAGE_WRITE_SETTINGS) intent.data = "package:${app.packageName}".toUri()
        try { app.startActivity(intent) }
        catch (_: Exception) { runCatching { app.startActivity(Intent(Settings.ACTION_SETTINGS).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)) }; message.value = "Откройте раздел разрешений в системных настройках" }
    }
    fun exportSettings() = app.settings.exportJson()
    fun calibrationJson() = app.diagnostics.calibrationJson()
    fun shareCalibration() { viewModelScope.launch {
        val raw = calibrationJson() ?: run { message.value = "Сначала выполните калибровку"; return@launch }
        val result = withContext(Dispatchers.IO) { runCatching {
            val file = File(app.filesDir, "diagnostics/shared-button-calibration.json")
            file.parentFile?.mkdirs(); file.writeText(raw, Charsets.UTF_8); file
        } }
        result.onSuccess { file ->
            val uri = runCatching { androidx.core.content.FileProvider.getUriForFile(app, "${app.packageName}.diagnostics", file) }
                .getOrElse { message.value = "Не удалось открыть файл отчёта для отправки"; return@onSuccess }
            val send = Intent(Intent.ACTION_SEND).setType("application/json").putExtra(Intent.EXTRA_STREAM, uri)
                .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            if (app.packageManager.queryIntentActivities(send, 0).isEmpty()) {
                message.value = "На часах нет приложения для отправки JSON. Сохраните файл или пришлите фото данных отчёта."
            } else runCatching { app.startActivity(Intent.createChooser(send, "Отчёт кнопки").addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)) }
                .onFailure { message.value = "Отправка файла недоступна. Сохраните JSON или пришлите фото данных отчёта." }
        }.onFailure { message.value = "Не удалось подготовить JSON отчёта" }
    } }
    fun exportLocal() { viewModelScope.launch {
        val raw = app.settings.exportJson()
        val result = withContext(Dispatchers.IO) { runCatching {
            val file = File(app.filesDir, "export/dx-buttons-settings.json")
            file.parentFile?.mkdirs(); file.writeText(raw, Charsets.UTF_8); file
        } }
        message.value = result.fold({ "JSON сохранён: files/export/dx-buttons-settings.json. Можно выгрузить через run-as в debug APK." }, { "Ошибка экспорта: ${it.message}" })
    } }
    fun importLocal() { viewModelScope.launch {
        val result = withContext(Dispatchers.IO) { runCatching {
            val file = File(app.filesDir, "import/dx-buttons-settings.json")
            require(file.length() in 1..131_072) { "Нет файла или он слишком большой" }; file.readText(Charsets.UTF_8)
        } }
        result.onSuccess(::importSettings).onFailure { message.value = "Ошибка импорта: ${it.message}" }
    } }
    fun importSettings(raw: String) { viewModelScope.launch { runCatching { app.settings.importJson(raw) }.onSuccess { message.value = "Импорт завершён. Повторите калибровку на этих часах." }.onFailure { message.value = "Импорт отклонён: ${it.message}" } } }
    fun reset() { viewModelScope.launch { app.settings.reset(); message.value = "Настройки сброшены" } }
    fun availability(action: Action) = AndroidActionPlatform(app, app.apps, DxButtonAccessibilityService.current()).availability(action)
    fun limitations() = CompatibilityChecker(app).limitations(settings.value)
    fun metadata() = CompatibilityChecker(app).metadata()
}

class GestureSettingsViewModel(application: Application) : AndroidViewModel(application) {
    private val repository = (application as DxApplication).settings
    val settings = repository.settings
    fun set(gesture: Gesture, transform: (GestureSetting) -> GestureSetting) { viewModelScope.launch { runCatching { repository.update { it.withGesture(gesture, transform(it.gesture(gesture))) } }.onFailure { repository.error.value = "Назначение не сохранено: ${it.message}" } } }
    fun restore(gesture: Gesture) { viewModelScope.launch { runCatching { repository.update { it.withGesture(gesture, GestureSetting()).copy(holdMs = if(gesture == Gesture.HOLD) 650 else it.holdMs, gapMs = if(gesture != Gesture.HOLD) 350 else it.gapMs) } }.onFailure { repository.error.value = "Сброс жеста не сохранён: ${it.message}" } } }
}
