package com.dynorixz.buttonscontrol

import android.accessibilityservice.AccessibilityServiceInfo
import android.content.ComponentName
import android.content.Context
import android.os.Build
import android.view.accessibility.AccessibilityManager
import com.dynorixz.buttonscontrol.core.*

class CompatibilityChecker(private val context: Context) {
    fun enabled(): Boolean {
        val own = ComponentName(context, DxButtonAccessibilityService::class.java)
        return context.getSystemService(AccessibilityManager::class.java)
            .getEnabledAccessibilityServiceList(AccessibilityServiceInfo.FEEDBACK_ALL_MASK).any { it.resolveInfo.serviceInfo.let { info -> ComponentName(info.packageName, info.name) == own } }
    }
    fun competingFilters() = context.getSystemService(AccessibilityManager::class.java)
        .getEnabledAccessibilityServiceList(AccessibilityServiceInfo.FEEDBACK_ALL_MASK).count {
            it.resolveInfo.serviceInfo.packageName != context.packageName && it.flags and AccessibilityServiceInfo.FLAG_REQUEST_FILTER_KEY_EVENTS != 0
        }
    fun metadata(): List<Pair<String, String>> = listOf(
        "Модель" to "${Build.MANUFACTURER} ${Build.MODEL}", "Android" to "${Build.VERSION.RELEASE} · API ${Build.VERSION.SDK_INT}",
        "Wear OS" to "${if (context.packageManager.hasSystemFeature("android.hardware.type.watch")) "Wear-устройство" else "Не часы"}; версия не публикуется универсальным API",
        "One UI Watch" to "Не определена публичным API", "Прошивка" to Build.DISPLAY,
        "Экран" to if (context.resources.configuration.isScreenRound) "Круглый" else "Прямоугольный"
    )
    fun limitations(s: DxSettings): List<String> = buildList {
        if (!enabled()) add("Включите службу вручную в специальных возможностях.")
        if (s.button == null) add("Нижняя кнопка не подтверждена. Нужна пара DOWN/UP с аппаратного источника; Home/Power запрещены.")
        if (s.face == null) add("Двойной и тройной жесты требуют калибровки циферблата.")
        if (Build.MANUFACTURER.equals("samsung", true)) add("Удержание Back может открывать Pay/Wallet. В режиме наблюдения системный обработчик остаётся активен; в Health и тренировках проверьте конфликт отдельно.")
        if (s.mode == InputMode.OBSERVE) add(if (s.holdOnTimer) "Наблюдение: события проходят системе. Удержание запускается через ${s.holdMs} мс; потерянный UP может вызвать ложное действие. Доступно подтверждение отпусканием." else "Наблюдение: удержание подтверждается повтором DOWN или отпусканием; без повторов запускается после UP.")
        else add("Перехват Back: одиночный жест заменён GLOBAL_ACTION_BACK; исходная настройка Samsung «Недавние» не воспроизводится. Решение системы вне Accessibility не подавляется гарантированно.")
        add("Выключенный экран, AOD и блокировка: жесты отключены. Для удержания калибровка циферблата не нужна. Root и постоянный ADB не используются.")
    }
}
