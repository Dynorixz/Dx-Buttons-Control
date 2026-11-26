package com.dynorixz.buttonscontrol.ui

import android.provider.Settings
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.Crossfade
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.dp
import androidx.core.graphics.drawable.toBitmap
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.wear.compose.material3.*
import com.dynorixz.buttonscontrol.*
import com.dynorixz.buttonscontrol.R
import com.dynorixz.buttonscontrol.core.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

@Composable fun DxApp(vm: MainViewModel, startScreen: String = "home") {
    val ready by vm.ready.collectAsStateWithLifecycle()
    if (!ready) { DxList("Dx Buttons Control") { item { DxInfo("Загрузка локальных настроек…") } }; return }
    val s by vm.settings.collectAsStateWithLifecycle()
    val d by vm.diagnostics.collectAsStateWithLifecycle()
    val message by vm.message.collectAsStateWithLifecycle()
    val storageError by vm.storageError.collectAsStateWithLifecycle()
    var route by rememberSaveable { mutableStateOf(startScreen) }
    var target by rememberSaveable { mutableStateOf("HOLD") }
    var category by rememberSaveable { mutableStateOf("APPS") }
    var configureId by rememberSaveable { mutableStateOf("toast") }
    var arg by rememberSaveable { mutableStateOf("") }
    var step by rememberSaveable { mutableIntStateOf(0) }
    var sequenceTarget by rememberSaveable { mutableStateOf("HOLD") }
    var sequence by remember { mutableStateOf<List<Action>>(emptyList()) }
    val gestureModel: GestureSettingsViewModel = viewModel()
    val scope = rememberCoroutineScope()
    val context = LocalContext.current
    val exporter = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("application/json")) { uri ->
        if (uri != null) scope.launch {
            val raw = vm.exportSettings()
            val result = withContext(Dispatchers.IO) { runCatching { context.contentResolver.openOutputStream(uri)?.use { it.write(raw.toByteArray(Charsets.UTF_8)) } ?: error("Файл недоступен") } }
            vm.message.value = if(result.isSuccess) "Настройки экспортированы" else "Ошибка экспорта: ${result.exceptionOrNull()?.message}"
        }
    }
    val reportExporter = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("application/json")) { uri ->
        val raw = vm.calibrationJson()
        if (uri != null && raw != null) scope.launch {
            val result = withContext(Dispatchers.IO) { runCatching {
                context.contentResolver.openOutputStream(uri)?.use { it.write(raw.toByteArray(Charsets.UTF_8)) } ?: error("Файл недоступен")
            } }
            vm.message.value = if(result.isSuccess) "Отчёт сохранён" else "Не удалось сохранить отчёт"
        }
    }
    val importer = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) scope.launch {
            val result = withContext(Dispatchers.IO) { runCatching {
                context.contentResolver.openInputStream(uri)?.use { input ->
                    val buffer = ByteArray(131_073)
                    var count = 0
                    while (count < buffer.size) {
                        val read = input.read(buffer, count, buffer.size - count)
                        if (read < 0) break
                        count += read
                    }
                    require(count <= 131_072) { "Файл слишком большой" }; buffer.copyOf(count).toString(Charsets.UTF_8)
                } ?: error("Файл недоступен")
            } }
            result.onSuccess(vm::importSettings).onFailure { vm.message.value = "Ошибка чтения: ${it.message}" }
        }
    }
    fun saveAction(action: Action) {
        when {
            target == "sequence" -> { sequence = sequence + action; route = "sequence" }
            target.startsWith("quick") -> {
                val index = target.removePrefix("quick").toInt()
                vm.update { it.copy(quickActions = it.quickActions.toMutableList().apply { this[index] = action }) }; route = "quick_edit"
            }
            else -> { val gesture = Gesture.valueOf(target); gestureModel.set(gesture) { it.copy(action = action, enabled = action.id != "none") }; route = "gesture" }
        }
    }
    fun choose(id: String) {
        when(id) {
            "app", "music_app", "stopwatch", "find_phone" -> { configureId = id; vm.loadApps(); route = "apps" }
            "sequence" -> { sequenceTarget = target; sequence = emptyList(); route = "sequence" }
            "toast", "brightness", "system_intent", "shortcut" -> { configureId = id; arg = if(id == "brightness") "128" else ""; route = "argument" }
            else -> saveAction(Action(id))
        }
    }
    BackHandler(enabled = route != "home") {
        route = when(route) {
            "category", "apps", "argument" -> "actions"
            "actions" -> if (target == "sequence") "sequence" else if(target.startsWith("quick")) "quick_edit" else "gesture"
            "sequence" -> { target = sequenceTarget; "actions" }
            "permissions", "advanced", "rhythm", "calibration", "quick_edit", "exclusions" -> "settings"
            "data", "capture", "diagnostics", "help", "reset" -> "advanced"
            "json" -> "data"
            "button_report" -> "calibration"
            else -> "home"
        }
    }
    val onboarding = !s.onboardingComplete && route == "home"
    Crossfade(targetState = if(onboarding) "onboarding" else route, label = "screen") { screen ->
        when(screen) {
            "onboarding" -> OnboardingScreen(vm, s, d, step, { step = it }, { route = it })
            "home" -> HomeScreen(vm, s, d, storageError, message, onGesture = { target = it.name; route = "gesture" }, navigate = { route = it })
            "gesture" -> GestureScreen(vm, gestureModel, s, Gesture.valueOf(target), { route = "actions" })
            "actions" -> DxList("Выбрать действие") {
                ActionCategory.entries.forEach { c -> item { DxCard(c.title, icon = if(c == ActionCategory.APPS) "apps" else "bolt") { if(c == ActionCategory.APPS) choose("app") else { category = c.name; route = "category" } } } }
            }
            "category" -> DxList(ActionCategory.valueOf(category).title) {
                ActionRegistry.specs.filter { it.category.name == category && !(target == "sequence" && it.id == "sequence") }.forEach { spec ->
                    item { DxCard(spec.title, detail = when(spec.id) { "stopwatch", "find_phone" -> "Выбрать приложение"; "none" -> "Системное поведение"; else -> null }, icon = spec.icon) { choose(spec.id) } }
                }
            }
            "apps" -> AppsScreen(vm, s, onSelect = { app ->
                vm.update { it.copy(recentApps = (listOf(app.packageName) + it.recentApps.filterNot { old -> old == app.packageName }).take(12)) }
                saveAction(Action(configureId, app.packageName))
            })
            "argument" -> DxList(ActionRegistry.spec(configureId)?.title ?: "Действие") {
                item { DxInfo(ActionRegistry.spec(configureId)?.argumentHint.orEmpty()) }
                item { DxInput(arg, "Введите значение") { arg = it } }
                if (configureId == "system_intent") {
                    listOf(Settings.ACTION_WIFI_SETTINGS, Settings.ACTION_BLUETOOTH_SETTINGS, Settings.ACTION_DISPLAY_SETTINGS, Settings.ACTION_BATTERY_SAVER_SETTINGS, Settings.ACTION_DATE_SETTINGS).forEach { value -> item { DxCard(value.removePrefix("android.settings.")) { arg = value } } }
                }
                if (configureId == "brightness") item { DxButton("Разрешить изменение настроек", { vm.permissions(Settings.ACTION_MANAGE_WRITE_SETTINGS) }) }
                item { DxButton("Назначить", { saveAction(Action(configureId, arg)) }, enabled = arg.isNotBlank() && (configureId != "brightness" || arg.toIntOrNull() in 1..255)) }
                item { DxInfo(vm.availability(Action(configureId, arg)).message) }
            }
            "sequence" -> DxList("Последовательность", "До 6 действий; остановка при ошибке") {
                sequence.forEachIndexed { index, action -> item { DxCard("${index + 1}. ${ActionRegistry.title(action)}", "Нажмите, чтобы удалить", ActionRegistry.spec(action.id)?.icon ?: "bolt") { sequence = sequence.filterIndexed { i, _ -> i != index } } } }
                item { DxButton("Добавить действие", { target = "sequence"; route = "actions" }, sequence.size < 6) }
                item { DxButton("Сохранить", { target = sequenceTarget; saveAction(Action("sequence", steps = sequence)) }, sequence.isNotEmpty()) }
            }
            "hold_test" -> DxList("Тест нижней кнопки", "На включённом циферблате") {
                item { DxInfo(if(d.connected) "Служба подключена" else "Включите службу Dx в специальных возможностях") }
                item { DxButton("1. Определить кнопку", { route = "calibration" }) }
                item { DxInfo(s.button?.let { "key ${it.keyCode} · scan ${it.scanCode} · ${it.deviceName}" } ?: "Кнопка ещё не сохранена") }
                item { DxButton("2. Назначить фонарик", vm::setupHoldTest, d.connected && s.button != null) }
                item { DxInfo("Откройте главный циферблат и держите нижнюю кнопку 1–2 секунды. Через 650 мс должен появиться белый экран. Калибровка циферблата для удержания не нужна.") }
                item { DxInfo(d.buttonStatus) }
                item { DxInfo("Жест: ${d.lastGesture}\nРезультат: ${d.result}") }
                item { DxInfo(message) }
                item { DxButton("Диагностика", { route = "diagnostics" }) }
            }
            "calibration" -> CalibrationScreen(vm, s, d) { route = "button_report" }
            "diagnostics" -> DiagnosticsScreen(vm, d) { route = "button_report" }
            "button_report" -> ButtonReportScreen(vm, s, d, message) {
                runCatching { reportExporter.launch("dx-button-calibration.json") }.onFailure {
                    vm.message.value = "На часах нет выбора файлов. Используйте отправку JSON или пришлите фото отчёта."
                }
            }
            "settings", "permissions", "advanced", "data", "rhythm" -> SettingsScreen(vm, s, message, navigate = { route = it }, export = {
                runCatching { exporter.launch("dx-buttons-settings.json") }.onFailure { vm.message.value = "На часах нет файлового провайдера. JSON доступен на экране Редактор JSON и через ADB." }
            }, import = {
                runCatching { importer.launch(arrayOf("application/json", "text/plain", "application/octet-stream")) }.onFailure { vm.message.value = "На часах нет файлового провайдера. Используйте Редактор JSON." }
            }, page = screen)
            "json" -> {
                var raw by remember { mutableStateOf(vm.exportSettings()) }
                DxList("Редактор JSON", "Локальный импорт / экспорт") {
                    item { DxInfo("Экспорт сохраняет файл JSON локально. В debug APK его можно выгрузить через run-as. Импорт сбрасывает калибровку устройства.") }
                    item { DxButton("Сохранить локальный JSON", vm::exportLocal) }
                    item { DxButton("Импорт из локального файла", vm::importLocal) }
                    item { DxInput(raw, "JSON настроек") { raw = it } }
                    item { DxButton("Импортировать JSON", { vm.importSettings(raw) }) }
                    item { DxInfo(message) }
                }
            }
            "exclusions" -> ExclusionsScreen(vm, s)
            "quick_edit" -> DxList("DX Quick Actions", "4–6 команд") {
                s.quickActions.forEachIndexed { index, action -> item { DxCard("${index + 1}. ${ActionRegistry.title(action)}", action.argument.takeIf { it.isNotBlank() }, ActionRegistry.spec(action.id)?.icon ?: "bolt") { target = "quick$index"; route = "actions" } } }
                item { DxButton("Добавить команду", { vm.update { it.copy(quickActions = it.quickActions + Action("none")) } }, s.quickActions.size < 6) }
                item { DxButton("Удалить последнюю", { vm.update { it.copy(quickActions = it.quickActions.dropLast(1)) } }, s.quickActions.size > 4) }
                item { DxButton("Открыть меню", { vm.test(Action("quick_actions")) }) }
            }
            "capture" -> DxList("Режим перехвата", "Только подтверждённый KEYCODE_BACK") {
                item { DxInfo("Чтобы подавить событие, служба должна забрать DOWN до того, как станет известен жест. Одиночное нажатие будет заменено системной командой Back, с задержкой на циферблате. Это не воспроизводит настройку Samsung «Недавние». Удержание запускается по таймеру; потерянный UP может вызвать ложное удержание. Сочетание с другой кнопкой отменяет действие Dx, но уже перехваченный DOWN вернуть нельзя. Системные действия прошивки могут остаться активны.") }
                item { DxButton("Включить с этими условиями", { vm.update { it.copy(mode = InputMode.CAPTURE_BACK) }; route = "settings" }, s.button?.keyCode == 4) }
                item { DxInfo("Для неназначенного длительного или тройного жеста в этом режиме используется один системный Back. Для полного штатного поведения кнопки выберите наблюдение.") }
                item { DxButton("Наблюдение: штатная кнопка", { vm.update { it.copy(mode = InputMode.OBSERVE) }; route = "settings" }) }
            }
            "reset" -> DxList("Сброс параметров") {
                item { DxInfo("Назначения, исключения и калибровка будут удалены. Служба останется включённой в системе.") }
                item { DxButton("Сбросить", { vm.reset(); step = 0; route = "home" }) }
            }
            "about" -> AboutScreen(vm)
            "help" -> DxList("Как работает Dx") {
                item { DxInfo("Удержание работает на доступных экранах. Двойное и тройное — на главном циферблате. Одиночное нажатие сохраняет системное поведение.") }
                item { DxInfo("При тройном нажатии двойное действие не выполняется. У двойного есть короткая задержка для ожидания третьего нажатия.") }
                vm.limitations().forEach { item { DxInfo(it) } }
            }
        }
    }
}
