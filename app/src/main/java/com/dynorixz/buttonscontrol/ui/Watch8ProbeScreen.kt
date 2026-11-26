package com.dynorixz.buttonscontrol.ui

import android.os.Build
import android.provider.Settings
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.dynorixz.buttonscontrol.BuildConfig
import com.dynorixz.buttonscontrol.DiagnosticsState
import com.dynorixz.buttonscontrol.MainViewModel
import com.dynorixz.buttonscontrol.buttonDeviceDetails

@Composable
internal fun Watch8ProbeScreen(
    vm: MainViewModel,
    diagnostics: DiagnosticsState,
    onReport: () -> Unit,
    onExit: () -> Unit
) {
    val probe by vm.watch8Probe.state.collectAsStateWithLifecycle()
    val report = diagnostics.calibrationReport?.takeIf {
        probe.serviceStarted && !diagnostics.reportIsRestored && it.appVersion == BuildConfig.VERSION_NAME
    }?.report
    val foreground = probe.appUp ?: probe.appDown
    val serviceState = when {
        diagnostics.connected -> "Подключена"
        diagnostics.enabled -> "Включена в Android, но нет подключения"
        else -> "Выключена"
    }
    val summary = when {
        !probe.started -> "Начните тест, затем один раз нажмите нижнюю кнопку."
        report?.accepted == true -> "Служба получила кнопку. Проверки пройдены."
        report?.down != null || report?.up != null -> "Служба получила события. Смотрите причину ниже."
        probe.listening && probe.serviceStarted -> "Нажмите и отпустите нижнюю кнопку один раз."
        probe.listening -> "Служба не подключена. Нажмите нижнюю кнопку для проверки окна."
        !probe.serviceStarted -> "Тест начат без службы Dx."
        !diagnostics.connected && report == null -> "Служба отключилась во время теста."
        else -> "За время теста служба не получила кнопку."
    }
    DxList("Тест кнопки", "Watch8 Classic", modifier = Modifier.onPreviewKeyEvent {
        vm.watch8Probe.record(it.nativeKeyEvent)
        false
    }) {
        item { DxInfo("Служба Dx: $serviceState") }
        if (!diagnostics.connected) {
            item { DxButton("Включить службу", { vm.permissions(Settings.ACTION_ACCESSIBILITY_SETTINGS) }) }
        }
        item {
            DxButton(if (probe.listening) "Тест идёт…" else "Начать тест", {
                vm.refresh()
                vm.watch8Probe.start()
            }, enabled = !probe.listening)
        }
        item { DxInfo(summary) }
        if (probe.started) {
            val windowDown = if (probe.appDown != null) "да" else "нет"
            val windowUp = if (probe.appUp != null) "да" else "нет"
            val back = if (probe.systemBack) "да" else "нет"
            item { DxInfo("Окно Dx: DOWN $windowDown, UP $windowUp\nСистемный Back: $back") }
            if (report != null) {
                val serviceDown = if (report.down != null) "да" else "нет"
                val serviceUp = if (report.up != null) "да" else "нет"
                item { DxInfo("Служба: DOWN $serviceDown, UP $serviceUp\n${report.explanation()}") }
                if (diagnostics.candidate != null) item { DxButton("Это нижняя кнопка", vm::bindCandidate) }
                item { DxButton("Отчёт кнопки", onReport) }
                val serviceEvent = report.up ?: report.down
                if (serviceEvent != null) item { DxInfo(buttonDeviceDetails(serviceEvent, report.resolvedLookup)) }
            }
            if (foreground != null) {
                item { DxSection("Источник в окне Dx") }
                item { DxInfo(buttonDeviceDetails(foreground, probe.appLookup)) }
            }
        }
        item { DxInfo("Dx ${BuildConfig.VERSION_NAME}\n${Build.MANUFACTURER} ${Build.MODEL}\nAPI ${Build.VERSION.SDK_INT} · ${Build.DISPLAY}") }
        if (diagnostics.connected) {
            val capability = if (diagnostics.keyFilter) "есть" else "нет"
            item { DxInfo("Право на фильтр: $capability\nДругие фильтры: ${diagnostics.otherFilters}") }
        } else {
            item { DxButton("Обновить статус", vm::refresh) }
        }
        item { DxButton("В основное меню", onExit) }
    }
}
