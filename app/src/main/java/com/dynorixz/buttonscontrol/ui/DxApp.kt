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
                ActionCategory.entries.forEach { c -> item { DxCard(c.title, icon = if(c == ActionCategory.APPS) "apps" else "bolt") { category = c.name; route = "category" } } }
            }
            "category" -> DxList(ActionCategory.valueOf(category).title) {
                ActionRegistry.specs.filter { it.category.name == category && !(target == "sequence" && it.id == "sequence") }.forEach { spec ->
                    item { DxCard(spec.title, detail = when(spec.id) { "stopwatch", "find_phone" -> "Нет универсального Intent: выберите доступное приложение"; "none" -> "Системное поведение"; else -> null }, icon = spec.icon) { choose(spec.id) } }
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
            "calibration" -> CalibrationScreen(vm, s, d)
            "diagnostics" -> DiagnosticsScreen(vm, d)
            "settings" -> SettingsScreen(vm, s, message, navigate = { route = it }, export = {
                runCatching { exporter.launch("dx-buttons-settings.json") }.onFailure { vm.message.value = "На часах нет файлового провайдера. JSON доступен на экране Редактор JSON и через ADB." }
            }, import = {
                runCatching { importer.launch(arrayOf("application/json", "text/plain", "application/octet-stream")) }.onFailure { vm.message.value = "На часах нет файлового провайдера. Используйте Редактор JSON." }
            })
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
            "about" -> DxList("Dx Buttons Control", "Dynorixz Studio · ${BuildConfig.VERSION_NAME}") {
                item { Image(painterResource(R.drawable.ic_dx), "Dx", Modifier.size(48.dp)) }
                item { DxInfo("Менеджер нижней физической кнопки для Wear OS. Все данные остаются на часах. Интернет, Root, Shizuku и постоянный ADB не нужны.") }
                item { DxInfo("Accessibility используется только для аппаратных кнопок и структуры активных окон: package, class, ID и геометрия. Текст, пароли, переписки, контакты и содержимое уведомлений не читаются.") }
                item { DxInfo("Доступ к уведомлениям нужен только для MediaSession. Служба не обрабатывает содержимое уведомлений. Установленные пакеты видны локально для выбора приложения.") }
                item { DxButton("Специальные возможности", { vm.permissions(Settings.ACTION_ACCESSIBILITY_SETTINGS) }) }
            }
        }
    }
}

@Composable private fun HomeScreen(vm: MainViewModel, s: DxSettings, d: DiagnosticsState, storageError: String?, message: String, onGesture: (Gesture) -> Unit, navigate: (String) -> Unit) {
    DxList("Buttons Control", "Dynorixz Studio") {
        item { Image(painterResource(R.drawable.ic_dx), "Dx", Modifier.size(40.dp)) }
        item { DxCard(if(d.connected) "Служба подключена" else if(d.enabled) "Служба включена, ожидаем подключение" else "Служба выключена", s.button?.let { "Нижняя кнопка: ${it.keyCode}" } ?: "Нужна калибровка", "bolt") { vm.permissions(Settings.ACTION_ACCESSIBILITY_SETTINGS) } }
        item { DxInfo(if(d.face.onFace) "Циферблат определён" else "Циферблат: ${d.face.reason}") }
        storageError?.let { item { DxInfo(it) } }
        Gesture.entries.forEach { g ->
            val setting = s.gesture(g)
            item { DxCard(gestureTitle(g), "${if(g == Gesture.HOLD) "Глобально" else "Только циферблат"}\n${if(setting.enabled) ActionRegistry.title(setting.action) else "Не назначено"}", ActionRegistry.spec(setting.action.id)?.icon ?: "block") { onGesture(g) } }
        }
        if (message.isNotBlank()) item { DxInfo(message) }
        item { DxCard("Проверить удержание", "1. Определите кнопку · 2. Назначьте фонарик", "bolt") { navigate("hold_test") } }
        item { DxCard("Определить кнопку", icon = "bolt") { navigate("calibration") } }
        item { DxCard("DX Quick Actions", icon = "apps") { navigate("quick_edit") } }
        item { DxCard("Диагностика", d.result, "bolt") { navigate("diagnostics") } }
        item { DxCard("Настройки") { navigate("settings") } }
    }
}

@Composable private fun GestureScreen(vm: MainViewModel, gv: GestureSettingsViewModel, s: DxSettings, g: Gesture, select: () -> Unit) {
    var custom by rememberSaveable(g) { mutableStateOf("") }
    val setting = s.gesture(g)
    DxList(gestureTitle(g), if(g == Gesture.HOLD) "Глобально при доступных событиях" else "Только подтверждённый циферблат") {
        item { DxToggle("Активен", setting.enabled) { value -> gv.set(g) { it.copy(enabled = value) } } }
        item { DxCard(ActionRegistry.title(setting.action), setting.action.argument.takeIf { it.isNotBlank() }, ActionRegistry.spec(setting.action.id)?.icon ?: "block", select) }
        item { DxButton("Выбрать действие", select) }
        item { DxInfo(vm.availability(setting.action).message) }
        val values = if(g == Gesture.HOLD) listOf(400L, 500L, 650L, 800L, 1000L) else listOf(250L, 350L, 450L, 600L)
        values.forEach { value -> item { DxCard("$value мс", if((if(g == Gesture.HOLD) s.holdMs else s.gapMs) == value) "Выбрано" else null, "timer") { vm.update { if(g == Gesture.HOLD) it.copy(holdMs = value) else it.copy(gapMs = value) } } } }
        item { DxInput(custom, if(g == Gesture.HOLD) "400–5000 мс" else "150–1000 мс") { custom = it } }
        item { DxButton("Применить время", { custom.toLongOrNull()?.let { value -> vm.update { if(g == Gesture.HOLD) it.copy(holdMs = value) else it.copy(gapMs = value) } } }, custom.toLongOrNull() in (if(g == Gesture.HOLD) 400L..5000L else 150L..1000L)) }
        item { DxToggle("Тактильный отклик", setting.haptic) { value -> gv.set(g) { it.copy(haptic = value) } } }
        item { DxButton("Проверить действие", { vm.test(setting.action) }, setting.action.id != "none") }
        item { DxInfo("Проверка действия запускает команду напрямую. Физический жест проверяется на часах по экрану диагностики.") }
        if(g == Gesture.HOLD) {
            item { DxToggle("Запускать во время удержания", s.holdOnTimer, "Через ${s.holdMs} мс, даже без повторов DOWN") { value -> vm.update { it.copy(holdOnTimer = value) } } }
            item { DxInfo(if(s.holdOnTimer) "Один таймер после аппаратного DOWN. Смена окна, выключение экрана и другая кнопка отменяют жест. При потерянном UP возможно ложное удержание; отключите переключатель для подтверждения повтором или отпусканием." else "Удержание подтверждается повтором DOWN или отпусканием. Без повторов действие запускается при отпускании.") }
        }
        item { DxButton("Стандартные параметры", { gv.restore(g) }) }
    }
}

@Composable private fun AppsScreen(vm: MainViewModel, s: DxSettings, onSelect: (InstalledApp) -> Unit) {
    val apps by vm.apps.collectAsStateWithLifecycle()
    val loading by vm.appsLoading.collectAsStateWithLifecycle()
    var search by rememberSaveable { mutableStateOf("") }
    val filtered = apps.filter { search.isBlank() || it.label.contains(search, true) || it.packageName.contains(search, true) }
        .sortedWith(compareByDescending<InstalledApp> { it.packageName in s.favorites }.thenBy { s.recentApps.indexOf(it.packageName).takeIf { index -> index >= 0 } ?: 100 }.thenBy { it.label.lowercase() })
    DxList("Приложения", "Избранные и недавние — сверху") {
        item { DxInput(search, "Поиск") { search = it } }
        if(loading) item { DxInfo("Загружаем приложения…") }
        filtered.forEach { app -> item(key = app.packageName) {
            Column {
                Card(onClick = { if(app.launchable) onSelect(app) else vm.message.value = "У этого приложения нет публичной точки запуска. Выберите опубликованный ярлык." }, modifier = Modifier.fillMaxWidth()) {
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        val bitmap = remember(app.packageName, app.icon) { app.icon?.toBitmap(40, 40)?.asImageBitmap() }
                        bitmap?.let { Image(it, app.label, Modifier.size(30.dp)) }
                        Column(Modifier.weight(1f)) {
                            Text(app.label, style = MaterialTheme.typography.labelLarge)
                            Text(if(app.launchable) app.packageName else "Нет доступной Activity", style = MaterialTheme.typography.bodySmall)
                        }
                    }
                }
                DxToggle("В избранном", app.packageName in s.favorites) { checked -> vm.update { it.copy(favorites = if(checked) it.favorites + app.packageName else it.favorites - app.packageName) } }
            }
        } }
        if(!loading && filtered.isEmpty()) item { DxInfo("Приложения не найдены") }
    }
}

@Composable private fun CalibrationScreen(vm: MainViewModel, s: DxSettings, d: DiagnosticsState) {
    DxList("Определить кнопку") {
        item { DxInfo("Включите Accessibility. Начните проверку, нажмите и отпустите нижнюю кнопку, затем откройте Dx. Кнопка во время проверки проходит системе.") }
        item { DxButton("Получить DOWN / UP", vm::calibrateButton, d.connected) }
        item { DxInfo(d.lastKey) }
        item { DxInfo(d.calibration) }
        item { DxButton("Это нижняя кнопка", vm::bindCandidate, d.candidate != null) }
        item { DxInfo(s.button?.let { "Сохранено: key ${it.keyCode}, scan ${it.scanCode}." } ?: "Нижняя кнопка не подтверждена.") }
        item { DxInfo("На части Samsung нижняя кнопка передаётся как STEM_PRIMARY / 264 с устройства pmic_resin. Такая привязка разрешена в наблюдении; верхняя Power не назначается. Подтверждайте только физически нажатую нижнюю кнопку.") }
        item { DxButton("Калибровать циферблат за 7 с", vm::calibrateFace, d.connected) }
        item { DxInfo("После нажатия откройте главный циферблат. Используются только class, ID и геометрия. Затем проверьте, что индикатор не включается на уведомлениях, в списке приложений или быстрых настройках. Если структура совпадает, удалите калибровку; на этой прошивке безопасное определение недоступно.") }
        item { DxInfo(d.result) }
        item { DxInfo("Циферблат определён: ${if(d.face.onFace) "да" else "нет"}\n${d.face.reason}") }
        item { DxButton("Удалить калибровку циферблата", { vm.update { it.copy(face = null) } }, s.face != null) }
    }
}

@Composable private fun DiagnosticsScreen(vm: MainViewModel, d: DiagnosticsState) {
    DxList("Диагностика") {
        vm.metadata().forEach { (name, value) -> item { DxInfo("$name\n$value") } }
        item { DxInfo("Accessibility: включена ${d.enabled}, подключена ${d.connected}\nФильтр: capability ${d.keyFilter}, других фильтров ${d.otherFilters}") }
        item { DxInfo("Последняя кнопка\n${d.lastKey}") }
        item { DxInfo("Обработка кнопки\n${d.buttonStatus}") }
        item { DxInfo("Жест\n${d.lastGesture}") }
        item { DxInfo("Циферблат определён: ${d.face.onFace}\n${d.face.reason}") }
        item { DxInfo("Активное приложение\n${d.foreground}") }
        item { DxInfo("Выполнение\n${d.result}") }
        vm.limitations().forEach { item { DxInfo(it) } }
        d.entries.takeLast(12).reversed().forEach { row -> item { DxInfo("${row.uptimeMs} · ${row.kind}\n${row.message}") } }
        item { DxButton("Обновить статус службы", vm::refresh) }
    }
}

@Composable private fun SettingsScreen(vm: MainViewModel, s: DxSettings, message: String, navigate: (String) -> Unit, export: () -> Unit, import: () -> Unit) {
    DxList("Настройки") {
        item { DxToggle("Обрабатывать после подключения", s.runWhenConnected, "Android восстанавливает включённую службу после загрузки") { checked -> vm.update { it.copy(runWhenConnected = checked) } } }
        item { DxToggle("Вибрация", s.vibration) { checked -> vm.update { it.copy(vibration = checked) } } }
        item { DxToggle("Диагностический журнал", s.diagnostics, "80 записей в памяти; без текста экранов") { checked -> vm.update { it.copy(diagnostics = checked) } } }
        item { DxCard("Чувствительность", "Минимальное нажатие: ${s.debounceMs} мс", "timer") { vm.update { it.copy(debounceMs = when(it.debounceMs) { 25L -> 40; 40L -> 60; else -> 25 }) } } }
        item { DxCard("Исключения удержания", "${s.excludedPackages.size} приложений", "block") { vm.loadApps(); navigate("exclusions") } }
        item { DxCard("Обработка кнопки", if(s.mode == InputMode.OBSERVE) "Наблюдение" else "Перехват Back", "bolt") { navigate("capture") } }
        item { DxButton("Включить Accessibility", { vm.permissions(Settings.ACTION_ACCESSIBILITY_SETTINGS) }) }
        item { DxButton("Доступ к MediaSession", { vm.permissions(Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS) }) }
        item { DxButton("Доступ к Не беспокоить", { vm.permissions(Settings.ACTION_NOTIFICATION_POLICY_ACCESS_SETTINGS) }) }
        item { DxButton("Изменение яркости", { vm.permissions(Settings.ACTION_MANAGE_WRITE_SETTINGS) }) }
        item { DxButton("Экспорт JSON", export) }
        item { DxButton("Импорт JSON", import) }
        item { DxCard("Редактор JSON", "Резервный способ без файлового провайдера") { navigate("json") } }
        if(message.isNotBlank()) item { DxInfo(message) }
        item { DxCard("Сброс параметров", icon = "block") { navigate("reset") } }
        item { DxCard("О приложении", icon = "bolt") { navigate("about") } }
    }
}

@Composable private fun ExclusionsScreen(vm: MainViewModel, s: DxSettings) {
    val apps by vm.apps.collectAsStateWithLifecycle()
    var search by rememberSaveable { mutableStateOf("") }
    DxList("Исключения", "Удержание проходит без действия Dx") {
        item { DxInput(search, "Поиск приложения") { search = it } }
        apps.filter { search.isBlank() || it.label.contains(search, true) || it.packageName.contains(search, true) }.forEach { app -> item(key = app.packageName) {
            DxToggle(app.label, app.packageName in s.excludedPackages, app.packageName) { checked -> vm.update { it.copy(excludedPackages = if(checked) it.excludedPackages + app.packageName else it.excludedPackages - app.packageName) } }
        } }
    }
}

@Composable private fun OnboardingScreen(vm: MainViewModel, s: DxSettings, d: DiagnosticsState, step: Int, setStep: (Int) -> Unit, navigate: (String) -> Unit) {
    val titles = listOf("Добро пожаловать", "Как работает Dx", "Специальные возможности", "Определить нижнюю кнопку", "Калибровка жестов", "Назначьте действия", "Проверка на часах")
    DxList(titles[step], "${step + 1} / 7 · Dx Buttons Control") {
        when(step) {
            0 -> {
                item { Image(painterResource(R.drawable.ic_dx), "Dx", Modifier.size(48.dp)) }
                item { DxInfo("Dynorixz Studio\nУдержание — глобально. Двойное и тройное — на подтверждённом циферблате.") }
            }
            1 -> item { DxInfo("Dx получает события физической кнопки через Android Accessibility. В режиме наблюдения кнопка сохраняет штатное поведение. Прошивка может забрать событие раньше. Тексты экранов и уведомлений не читаются; сеть не используется.") }
            2 -> {
                item { DxInfo("Разрешение нужно для аппаратных событий и метаданных активного окна. Включите службу Dx вручную, затем вернитесь сюда.") }
                item { DxButton("Системные настройки", { vm.permissions(Settings.ACTION_ACCESSIBILITY_SETTINGS) }) }
                item { DxInfo(if(d.connected) "Служба подключена" else "Служба пока не подключена") }
            }
            3 -> {
                item { DxButton("Проверить кнопку", vm::calibrateButton, d.connected) }
                item { DxInfo(d.calibration) }
                item { DxInfo(d.lastKey) }
                item { DxButton("Подтверждаю нижнюю кнопку", vm::bindCandidate, d.candidate != null) }
                item { DxInfo(if(s.button != null) "Кнопка сохранена" else "Нажмите нижнюю кнопку и вернитесь в Dx. Home/Power не принимаются.") }
            }
            4 -> {
                item { DxInfo("Удержание ${s.holdMs} мс; между нажатиями ${s.gapMs} мс. Сначала запишите основной циферблат, затем проверьте короткие, двойные, тройные и длительные нажатия в диагностике. Начальные назначения на следующем шаге.") }
                item { DxButton("Циферблат: захват через 7 с", vm::calibrateFace, d.connected) }
                item { DxInfo(d.result) }
                listOf(400L, 500L, 650L, 800L, 1000L).forEach { time -> item { DxCard("Удержание $time мс", if(s.holdMs == time) "Выбрано" else null, "timer") { vm.update { it.copy(holdMs = time) } } } }
                item { DxCard("Интервал ${s.gapMs} мс", "Нажмите для 250 / 350 / 450 мс", "timer") { vm.update { it.copy(gapMs = when(it.gapMs) { 250L -> 350; 350L -> 450; else -> 250 }) } } }
            }
            5 -> {
                item { DxInfo("Начальный набор: удержание — DX Quick Actions, двойное — уведомления, тройное — фонарик. Любое действие можно изменить на главном экране.") }
                item { DxButton("Назначить начальные действия", { vm.update { it.copy(hold = GestureSetting(true, Action("quick_actions")), double = GestureSetting(true, Action("notifications")), triple = GestureSetting(true, Action("flashlight"))) } }) }
            }
            6 -> {
                item { DxInfo("Проверьте действия и физические жесты, затем возвращайтесь в приложение. Особенно проверьте Wallet, Health и стандартное одиночное Back. Результат последней команды: ${d.result}.") }
                item { DxButton("Проверить меню Dx", { vm.test(Action("quick_actions")) }) }
                item { DxCard("Открыть диагностику", icon = "bolt") { navigate("diagnostics") } }
                vm.limitations().forEach { item { DxInfo(it) } }
            }
        }
        if(step > 0) item { DxButton("Назад", { setStep(step - 1) }) }
        item { DxButton(if(step == 6) "Завершить настройку" else "Далее", { if(step == 6) vm.update { it.copy(onboardingComplete = true) } else setStep(step + 1) }) }
        item { DxButton("Продолжить без настройки", { vm.update { it.copy(onboardingComplete = true) } }) }
    }
}
