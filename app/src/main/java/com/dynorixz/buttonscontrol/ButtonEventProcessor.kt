package com.dynorixz.buttonscontrol

import android.os.Handler
import android.os.SystemClock
import android.view.KeyEvent
import com.dynorixz.buttonscontrol.core.*

/** Capturing is decided on initial DOWN and remains consistent until the matching UP.
 * Any second hardware key cancels the recognizer to avoid overriding combinations.
 */
class ButtonEventProcessor(
    private val handler: Handler, private val detector: WatchFaceStateDetector,
    private val settings: () -> DxSettings, emit: (Gesture) -> Unit, private val replayBack: () -> Unit,
    private val hardware: (Int) -> InputDeviceLookup,
    private val environment: ButtonDeviceEnvironment = ButtonDeviceEnvironment(),
    private val report: (String) -> Unit = {}
) {
    private val machine = GestureStateMachine(emit, replayBack)
    private var configured: GesturePolicy? = null
    private val ownership = KeySequenceOwnership()
    private val otherDown = mutableMapOf<Pair<Int, Int>, Long>()
    private val deadline = Runnable {
        machine.updateContext(detector.context(settings()))
        machine.advance(SystemClock.uptimeMillis())
        schedule()
    }
    fun contextChanged() { machine.updateContext(detector.context(settings())); schedule() }
    fun stop() { handler.removeCallbacks(deadline); machine.cancel(); configured = null }
    fun retainedConsumption(e: KeyEvent): Boolean? {
        val id = KeySequenceId(e.downTime, e.deviceId, e.keyCode, e.scanCode)
        return if (e.action == KeyEvent.ACTION_UP) ownership.release(id) else ownership.decision(id)
    }
    fun accept(e: KeyEvent): Boolean {
        val s = settings()
        val binding = s.button
        // A DOWN seen while device metadata were pending may be tracked as an
        // unknown key. Its UP must release that marker even if lookup is ready now.
        if (e.action == KeyEvent.ACTION_UP) otherDown.remove(e.deviceId to e.keyCode)
        val lookup = hardware(e.deviceId)
        val device = lookup.device
        val sample = e.toButtonKeyEvent()
        val matches = binding != null && ButtonSafety.matches(binding, sample, lookup, environment)
        if (!matches) {
            report(if (binding == null) "Нижняя кнопка ещё не сохранена: выполните калибровку" else "Получена другая кнопка: key ${e.keyCode}, scan ${e.scanCode}, ${device?.name}")
            val retained = retainedConsumption(e)
            if (e.action == KeyEvent.ACTION_DOWN) otherDown[e.deviceId to e.keyCode] = e.downTime
            machine.cancel(); handler.removeCallbacks(deadline)
            return retained == true
        }
        otherDown.entries.removeAll { e.eventTime - it.value > 6000 }
        val id = KeySequenceId(e.downTime, e.deviceId, e.keyCode, e.scanCode)
        val initial = e.action == KeyEvent.ACTION_DOWN && e.repeatCount == 0 && !ownership.tracked(id)
        val c = detector.context(s)
        if (BuildConfig.DEBUG) android.util.Log.d("DxButtons", "face=${c.onFace} epoch=${c.epoch} lag=${SystemClock.uptimeMillis() - e.eventTime}ms")
        val enabled = s.runWhenConnected && otherDown.isEmpty() && c.interactive
        if (initial) {
            val policy = GesturePolicy(s.holdMs, s.gapMs, s.debounceMs, s.assigned(Gesture.HOLD), s.assigned(Gesture.DOUBLE), s.assigned(Gesture.TRIPLE),
                s.mode == InputMode.CAPTURE_BACK && binding?.keyCode == KeyEvent.KEYCODE_BACK && enabled &&
                    ((s.assigned(Gesture.HOLD) && c.holdAllowed) || (c.onFace && (s.assigned(Gesture.DOUBLE) || s.assigned(Gesture.TRIPLE)))), s.holdOnTimer)
            if (configured != policy) { machine.configure(policy); configured = policy }
            ownership.begin(id, policy.captured)
        }
        report(when {
            !s.runWhenConnected -> "Обработка выключена в настройках Dx"
            otherDown.isNotEmpty() -> "Сочетание кнопок: жест отменён"
            !c.interactive -> "Экран выключен, AOD или часы заблокированы"
            !s.assigned(Gesture.HOLD) -> "Для удержания не выбрано активное действие"
            !c.holdAllowed -> "Удержание запрещено исключением или не удалось проверить исключения"
            else -> "Нижняя кнопка получена · ${s.holdMs} мс · ${if (s.holdOnTimer) "запуск при удержании" else "подтверждение повтором / отпусканием"}"
        })
        val consumed = ownership.decision(id) == true
        if (enabled) {
            machine.updateContext(c)
            machine.accept(ButtonSample(e.action, e.downTime, e.eventTime, e.repeatCount, e.isCanceled || e.flags and KeyEvent.FLAG_CANCELED_LONG_PRESS != 0))
        } else machine.cancel()
        if (e.action == KeyEvent.ACTION_UP) ownership.release(id)
        schedule()
        return consumed
    }
    private fun schedule() {
        handler.removeCallbacks(deadline)
        machine.nextDeadline()?.let { handler.postAtTime(deadline, maxOf(SystemClock.uptimeMillis() + 1, it)) }
    }
}
