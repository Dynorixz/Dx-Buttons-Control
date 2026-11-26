package com.dynorixz.buttonscontrol.ui

import android.provider.Settings
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.graphics.drawable.toBitmap
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.wear.compose.material3.*
import com.dynorixz.buttonscontrol.*
import com.dynorixz.buttonscontrol.R
import com.dynorixz.buttonscontrol.core.*

@Composable internal fun HomeScreen(vm: MainViewModel, s: DxSettings, d: DiagnosticsState, storageError: String?, message: String, onGesture: (Gesture) -> Unit, navigate: (String) -> Unit) {
    DxList("Buttons Control", if(d.connected && s.button != null) "Нижняя кнопка · Готово" else "Нужна настройка") {
        if(!d.connected) item { DxCard("Включить службу", icon = "bolt") { vm.permissions(Settings.ACTION_ACCESSIBILITY_SETTINGS) } }
        else if(s.button == null) item { DxCard("Настроить кнопку", icon = "bolt") { navigate("calibration") } }
        storageError?.let { item { DxInfo(it) } }
        Gesture.entries.forEach { gesture -> item {
            DxGestureCard(gesture, vm.actionLabel(s.gesture(gesture).action), s.assigned(gesture)) { onGesture(gesture) }
        } }
        item { DxCard("Настройки", icon = "settings") { navigate("settings") } }
        item { DxCard("О приложении", "Dynorixz Studio", "info") { navigate("about") } }
    }
}

@Composable internal fun GestureScreen(vm: MainViewModel, gv: GestureSettingsViewModel, s: DxSettings, g: Gesture, select: () -> Unit) {
    val setting = s.gesture(g)
    var advanced by rememberSaveable(g) { mutableStateOf(false) }
    var custom by rememberSaveable(g) { mutableStateOf("") }
    val time = if(g == Gesture.HOLD) s.holdMs else s.gapMs
    fun time(value: Long) { vm.update { if(g == Gesture.HOLD) it.copy(holdMs = value.coerceIn(400, 5000)) else it.copy(gapMs = value.coerceIn(150, 1000)) } }
    DxList(gestureTitle(g), if(g == Gesture.HOLD) "На любом экране" else "Только на циферблате") {
        item { DxCard(vm.actionLabel(setting.action), "Выбрать действие", ActionRegistry.spec(setting.action.id)?.icon ?: "apps", select) }
        item { DxToggle("Включено", setting.enabled) { checked -> gv.set(g) { it.copy(enabled = checked) } } }
        item { DxSection(if(g == Gesture.HOLD) "Порог удержания" else "Между нажатиями") }
        item { DxStepper("$time мс", { time(time - 50) }, { time(time + 50) }) }
        item { DxToggle("Вибрация", setting.haptic) { checked -> gv.set(g) { it.copy(haptic = checked) } } }
        item { DxButton("Проверить действие", { vm.test(setting.action) }, setting.action.id != "none") }
        if(g != Gesture.HOLD) item { DxCard("Настроить циферблат", icon = "home") { vm.calibrateFace() } }
        item { DxCard("Дополнительно", icon = "settings") { advanced = !advanced } }
        if(advanced) {
            item { DxInput(custom, "Время в миллисекундах") { custom = it } }
            item { DxButton("Применить", { custom.toLongOrNull()?.let(::time) }, custom.toLongOrNull() in (if(g == Gesture.HOLD) 400L..5000L else 150L..1000L)) }
            if(g == Gesture.HOLD) {
                item { DxToggle("Запуск до отпускания", s.holdOnTimer) { checked -> vm.update { it.copy(holdOnTimer = checked) } } }
                item { DxInfo("Таймер запускает удержание без повторных событий. Потерянное отпускание может вызвать ложное действие. Отключите для подтверждения отпусканием.") }
            }
            item { DxInfo(vm.availability(setting.action).message) }
            item { DxButton("Сбросить жест", { gv.restore(g) }) }
        }
    }
}

@Composable internal fun AppsScreen(vm: MainViewModel, s: DxSettings, onSelect: (InstalledApp) -> Unit) {
    val apps by vm.apps.collectAsStateWithLifecycle()
    val loading by vm.appsLoading.collectAsStateWithLifecycle()
    var search by rememberSaveable { mutableStateOf("") }
    val filtered = apps.filter { it.launchable && (search.isBlank() || it.label.contains(search,true) || it.packageName.contains(search,true)) }
        .sortedWith(compareByDescending<InstalledApp> { it.packageName in s.favorites }.thenBy { s.recentApps.indexOf(it.packageName).takeIf { index -> index >= 0 } ?: 100 }.thenBy { it.label.lowercase() })
    DxList("Приложения") {
        item { DxInput(search,"Поиск") { search = it } }
        if(loading) item { DxInfo("Загрузка…") }
        filtered.forEach { app -> item(key = app.packageName) {
            Row(Modifier.fillMaxWidth().heightIn(min=56.dp).clip(RoundedCornerShape(22.dp)).background(MaterialTheme.colorScheme.surfaceContainer).clickable { onSelect(app) }.padding(start=12.dp,end=4.dp,top=5.dp,bottom=5.dp), verticalAlignment=Alignment.CenterVertically, horizontalArrangement=Arrangement.spacedBy(9.dp)) {
                val bitmap = remember(app.packageName,app.icon) { app.icon?.toBitmap(36,36)?.asImageBitmap() }
                bitmap?.let { Image(it,null,Modifier.size(26.dp)) }
                Text(app.label, fontSize=14.sp,lineHeight=17.sp,maxLines=2,overflow=TextOverflow.Ellipsis,modifier=Modifier.weight(1f))
                Box(Modifier.size(40.dp).clickable { vm.update { it.copy(favorites=if(app.packageName in it.favorites) it.favorites-app.packageName else it.favorites+app.packageName) } },contentAlignment=Alignment.Center) {
                    Text(if(app.packageName in s.favorites) "★" else "☆",fontSize=20.sp,color=MaterialTheme.colorScheme.primary)
                }
            }
        } }
        if(!loading && filtered.isEmpty()) item { DxInfo("Ничего не найдено") }
    }
}

@Composable internal fun CalibrationScreen(vm: MainViewModel, s: DxSettings, d: DiagnosticsState, onReport: () -> Unit) {
    var details by rememberSaveable { mutableStateOf(false) }
    DxList("Настройка кнопки") {
        item { DxSection("Нижняя кнопка") }
        item { DxInfo(if(s.button != null) "Кнопка сохранена" else "Начните проверку, нажмите нижнюю кнопку и вернитесь сюда.") }
        item { DxButton("Определить кнопку",vm::calibrateButton,d.connected) }
        item { DxInfo(d.calibration) }
        if(d.candidate != null) item { DxButton("Это нижняя кнопка",vm::bindCandidate) }
        if(d.calibrationReport != null) item { DxCard("Отчёт кнопки", "Причина и данные InputDevice", "info", onReport) }
        item { DxSection("Двойное и тройное") }
        item { DxButton("Настроить циферблат",vm::calibrateFace,d.connected) }
        item { DxInfo("Откроем циферблат автоматически. Экран «Недавние» не принимается.") }
        if(d.result != "—") item { DxInfo(d.result) }
        item { DxCard("Технические данные",icon="info") { details = !details } }
        if(details) {
            item { DxInfo(d.lastKey) }
            item { DxInfo(buttonDeviceDetails(d.lastButtonEvent, d.lastDeviceLookup)) }
            item { DxInfo(s.button?.let { "key ${it.keyCode} · scan ${it.scanCode} · ${it.deviceName}" } ?: "Нет привязки") }
            item { DxInfo("Циферблат: ${d.face.reason}") }
            item { DxButton("Удалить профиль экрана",{ vm.update { it.copy(face=null) } },s.face != null) }
        }
    }
}

@Composable internal fun DiagnosticsScreen(vm: MainViewModel, d: DiagnosticsState, onReport: () -> Unit) {
    DxList("Диагностика") {
        item { DxInfo("Служба: ${if(d.connected) "подключена" else "выключена"}\nЦиферблат: ${d.face.reason}") }
        item { DxInfo("Кнопка\n${d.lastKey}") }
        item { DxInfo(buttonDeviceDetails(d.lastButtonEvent, d.lastDeviceLookup)) }
        if(d.calibrationReport != null) item { DxCard("Отчёт калибровки", icon = "info", onClick = onReport) }
        item { DxInfo("Жест: ${d.lastGesture}\n${d.result}") }
        item { DxInfo(d.buttonStatus) }
        item { DxSection("Устройство") }
        vm.metadata().forEach { (name,value) -> item { DxInfo("$name: $value") } }
        item { DxSection("Журнал") }
        d.entries.takeLast(10).reversed().forEach { row -> item { DxInfo("${row.kind} · ${row.message}") } }
        item { DxButton("Обновить",vm::refresh) }
    }
}

@Composable internal fun ButtonReportScreen(vm: MainViewModel, settings: DxSettings, d: DiagnosticsState, message: String, export: () -> Unit) {
    val envelope = d.calibrationReport
    DxList("Отчёт кнопки", BuildConfig.VERSION_NAME) {
        if(envelope == null) item { DxInfo("Отчёта пока нет. Выполните калибровку нижней кнопки.") }
        else {
            val report = envelope.report
            val event = report.up ?: report.down
            fun check(value: Boolean?) = when(value) { true -> "пройдено"; false -> "отказ"; null -> "не проверено" }
            if(d.reportIsRestored) item { DxInfo("Сохранённый отчёт. Для привязки повторите калибровку.") }
            item { DxInfo("${envelope.manufacturer} ${envelope.model}\nAndroid ${envelope.androidRelease} · API ${envelope.sdkInt}\nСборка: ${envelope.buildDisplay}\nAPK: ${envelope.appVersion}") }
            item { DxSection(if(report.accepted) "Проверки пройдены" else "Причина отказа") }
            item { DxInfo("ButtonSafety: ${check(report.verdict.bindingAllowed)}\nInputDevice: ${check(report.verdict.hardwareAllowed)}") }
            item { DxInfo(report.explanation()) }
            item { DxSection("Источник события") }
            item { DxInfo(buttonDeviceDetails(event, report.resolvedLookup)) }
            item { DxSection("Пара DOWN / UP") }
            item { DxInfo("DOWN: ${report.down?.let { "deviceId=${it.deviceId}, source=0x${it.source.toUInt().toString(16)}, downTime=${it.downTime}, eventTime=${it.eventTime}, repeat=${it.repeatCount}" } ?: "не получен"}\nМетаданные на DOWN: ${report.downLookup?.state ?: "нет"}") }
            item { DxInfo("UP: ${report.up?.let { "deviceId=${it.deviceId}, source=0x${it.source.toUInt().toString(16)}, downTime=${it.downTime}, eventTime=${it.eventTime}, canceled=${it.canceled}" } ?: "не получен"}\nМетаданные на UP: ${report.upLookup?.state ?: "нет"}\nПосле ожидания: ${report.resolvedLookup.state}") }
            item { DxInfo(if(report.candidate != null && settings.button == report.candidate) "Привязка сохранена" else "Эта проверка не сохранена как привязка") }
            item { DxButton("Поделиться JSON", vm::shareCalibration) }
            item { DxButton("Сохранить JSON", export) }
            if(message.isNotBlank()) item { DxInfo(message) }
            item { DxInfo("Если отправка файлов недоступна, сфотографируйте все данные этого отчёта. Тексты других экранов в отчёт не входят.") }
        }
    }
}

@Composable internal fun SettingsScreen(vm: MainViewModel, s: DxSettings, message: String, navigate: (String)->Unit, export:()->Unit, import:()->Unit, page:String="settings") {
    DxList(when(page) { "permissions"->"Разрешения"; "advanced"->"Дополнительно"; "data"->"Данные"; "rhythm"->"Ритм нажатий"; else->"Настройки" }) {
        when(page) {
            "settings" -> {
                item { DxToggle("Работает",s.runWhenConnected) { checked -> vm.update { it.copy(runWhenConnected=checked) } } }
                item { DxToggle("Вибрация",s.vibration) { checked -> vm.update { it.copy(vibration=checked) } } }
                item { DxCard("Кнопка и циферблат",icon="home") { navigate("calibration") } }
                item { DxCard("Ритм нажатий","${s.gapMs} мс","timer") { navigate("rhythm") } }
                item { DxCard("Быстрые действия",icon="apps") { navigate("quick_edit") } }
                item { DxCard("Исключения", "${s.excludedPackages.size} приложений","block") { vm.loadApps(); navigate("exclusions") } }
                item { DxCard("Разрешения",icon="lock") { navigate("permissions") } }
                item { DxCard("Дополнительно",icon="settings") { navigate("advanced") } }
            }
            "permissions" -> {
                item { DxButton("Служба Dx",{ vm.permissions(Settings.ACTION_ACCESSIBILITY_SETTINGS) }) }
                item { DxButton("Управление музыкой",{ vm.permissions(Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS) }) }
                item { DxButton("Не беспокоить",{ vm.permissions(Settings.ACTION_NOTIFICATION_POLICY_ACCESS_SETTINGS) }) }
                item { DxButton("Яркость",{ vm.permissions(Settings.ACTION_MANAGE_WRITE_SETTINGS) }) }
            }
            "advanced" -> {
                item { DxCard("Диагностика",icon="info") { navigate("diagnostics") } }
                item { DxToggle("Записывать журнал",s.diagnostics) { checked -> vm.update { it.copy(diagnostics=checked) } } }
                item { DxCard("Режим кнопки",if(s.mode==InputMode.OBSERVE) "Штатное поведение" else "Перехват Back","bolt") { navigate("capture") } }
                item { DxCard("Импорт и экспорт",icon="apps") { navigate("data") } }
                item { DxCard("Как работает Dx",icon="info") { navigate("help") } }
                item { DxCard("Сбросить настройки",icon="block") { navigate("reset") } }
            }
            "data" -> {
                item { DxButton("Экспорт JSON",export) }; item { DxButton("Импорт JSON",import) }
                item { DxCard("Редактор JSON",icon="settings") { navigate("json") } }
            }
            "rhythm" -> {
                item { DxSection("Между нажатиями") }
                item { DxStepper("${s.gapMs} мс",{ vm.update { it.copy(gapMs=(it.gapMs-50).coerceAtLeast(150)) } },{ vm.update { it.copy(gapMs=(it.gapMs+50).coerceAtMost(1000)) } }) }
                item { DxInfo("Быстро: 250 мс. Обычно: 350 мс. Неспешно: 500 мс.") }
                item { DxButton("350 мс",{ vm.update { it.copy(gapMs=350) } }) }
            }
        }
        if(message.isNotBlank() && page=="data") item { DxInfo(message) }
    }
}

@Composable internal fun ExclusionsScreen(vm:MainViewModel,s:DxSettings) {
    val apps by vm.apps.collectAsStateWithLifecycle()
    var search by rememberSaveable { mutableStateOf("") }
    DxList("Исключения") {
        item { DxInput(search,"Поиск") { search=it } }
        apps.filter { it.launchable && (search.isBlank() || it.label.contains(search,true)) }.forEach { app -> item(key=app.packageName) {
            DxToggle(app.label,app.packageName in s.excludedPackages) { checked -> vm.update { it.copy(excludedPackages=if(checked) it.excludedPackages+app.packageName else it.excludedPackages-app.packageName) } }
        } }
    }
}

@Composable internal fun AboutScreen(vm:MainViewModel) {
    val status by vm.linkStatus.collectAsStateWithLifecycle()
    DxList("О приложении") {
        item { Image(painterResource(R.drawable.ic_dx),null,Modifier.size(36.dp)) }
        item { Text("Dx Buttons Control",fontSize=15.sp,fontWeight=FontWeight.SemiBold,textAlign=TextAlign.Center) }
        item { Text("Dynorixz Studio · ${BuildConfig.VERSION_NAME}",fontSize=11.sp,color=MaterialTheme.colorScheme.onSurfaceVariant,textAlign=TextAlign.Center) }
        item { DxCard("Telegram","@dynorixz","send") { vm.openAuthorLink(AuthorLinks.TELEGRAM) } }
        item { DxCard("GitHub","@dynorixz","code") { vm.openAuthorLink(AuthorLinks.GITHUB) } }
        item { DxCard("Поддержать","CloudTips","heart") { vm.openAuthorLink(AuthorLinks.SUPPORT) } }
        item { DxInfo(if(status.isBlank()) "Ссылки открываются на телефоне." else status) }
        item { DxCard("Приватность",icon="lock") { vm.linkStatus.value="Всё хранится на часах. Тексты экранов, переписки и уведомления не читаются. Accessibility нужен только для кнопок и признаков окна." } }
    }
}

@Composable internal fun OnboardingScreen(vm:MainViewModel,s:DxSettings,d:DiagnosticsState,step:Int,setStep:(Int)->Unit,navigate:(String)->Unit) {
    val current=step.coerceIn(0,2)
    DxList(when(current) { 0->"Добро пожаловать"; 1->"Разрешить кнопки"; else->"Нижняя кнопка" },"${current+1} / 3") {
        when(current) {
            0 -> { item { Image(painterResource(R.drawable.ic_dx),null,Modifier.size(40.dp)) }; item { DxInfo("Назначьте действие на удержание, двойное и тройное нажатие нижней кнопки.") } }
            1 -> {
                item { DxInfo("Включите службу Dx. Она получает события кнопок и признаки окна. Содержимое экранов не читается.") }
                item { DxButton("Включить службу",{ vm.permissions(Settings.ACTION_ACCESSIBILITY_SETTINGS) }) }
            }
            2 -> {
                item { DxButton("Определить кнопку",{ navigate("calibration") },d.connected) }
                item { DxInfo("После настройки выберите действие в карточке нужного жеста.") }
            }
        }
        item { DxButton(if(current==2) "Начать" else "Далее",{ if(current==2) vm.update { it.copy(onboardingComplete=true) } else setStep(current+1) }) }
        item { DxCard("Настроить позже",icon="back") { vm.update { it.copy(onboardingComplete=true) } } }
    }
}
