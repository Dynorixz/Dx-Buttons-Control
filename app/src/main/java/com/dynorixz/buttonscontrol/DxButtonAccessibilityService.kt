package com.dynorixz.buttonscontrol

import android.accessibilityservice.AccessibilityService
import android.accessibilityservice.AccessibilityServiceInfo
import android.content.Intent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.IntentFilter
import android.os.Handler
import android.os.Looper
import android.view.KeyEvent
import android.view.accessibility.AccessibilityEvent
import androidx.core.content.ContextCompat
import com.dynorixz.buttonscontrol.core.*
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.collectLatest
import java.lang.ref.WeakReference

class DxButtonAccessibilityService : AccessibilityService() {
    private val handler = Handler(Looper.getMainLooper())
    // Queue commands until after onKeyEvent returns to Android's input filter.
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main)
    private val app get() = application as DxApplication
    private lateinit var detector: WatchFaceStateDetector
    private lateinit var processor: ButtonEventProcessor
    private var calibrating = false
    private data class CalibrationDown(val downTime: Long, val deviceId: Int, val keyCode: Int, val scanCode: Int)
    private var calibrationDown: CalibrationDown? = null
    private var actionJob: Job? = null
    private val calibrationTimeout = Runnable {
        calibrating = false
        calibrationDown = null
        if (app.diagnostics.state.value.candidate == null) app.diagnostics.change { it.copy(calibration = "Полная пара DOWN/UP не получена. Проверьте службу, другую службу фильтрации и ограничения прошивки.") }
    }
    private val faceCapture = Runnable {
        detector.inspect(app.settings.settings.value, captureFace = true)
        val d = app.diagnostics.state.value
        val window = d.rawSnapshot
        if (window == null || window.packageName == packageName || window.viewIds.isEmpty() || !getSystemService(android.os.PowerManager::class.java).isInteractive || getSystemService(android.app.KeyguardManager::class.java).isKeyguardLocked) {
            app.diagnostics.result(ActionResult(false, "Циферблат не подтверждён: нет отличительных ID окна. На этой прошивке жесты циферблата могут быть недоступны."))
        } else scope.launch {
            val signature = FaceSignature(window.packageName, window.className, window.rootClass, window.viewIds, window.topology)
            app.settings.update { it.copy(face = signature) }
            app.diagnostics.result(ActionResult(true, "Сохранено окно, показанное вами как циферблат. Проверьте индикатор на циферблате, в меню, уведомлениях и быстрых настройках."))
        }
    }
    private val screenReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) { processor.contextChanged() }
    }
    override fun onServiceConnected() {
        super.onServiceConnected()
        instance = WeakReference(this)
        serviceInfo = serviceInfo.apply { flags = flags or AccessibilityServiceInfo.FLAG_REQUEST_FILTER_KEY_EVENTS or AccessibilityServiceInfo.FLAG_RETRIEVE_INTERACTIVE_WINDOWS or AccessibilityServiceInfo.FLAG_REPORT_VIEW_IDS }
        detector = WatchFaceStateDetector(this, app.diagnostics)
        processor = ButtonEventProcessor(handler, detector, { app.settings.settings.value }, ::fire,
            { performGlobalAction(GLOBAL_ACTION_BACK) }) { status ->
            app.diagnostics.change { it.copy(buttonStatus = status) }
            if (BuildConfig.DEBUG) android.util.Log.d("DxButtons", status)
        }
        app.diagnostics.change { it.copy(connected = true, enabled = true,
            keyFilter = serviceInfo.capabilities and AccessibilityServiceInfo.CAPABILITY_CAN_REQUEST_FILTER_KEY_EVENTS != 0,
            otherFilters = CompatibilityChecker(this).competingFilters()) }
        ContextCompat.registerReceiver(this, screenReceiver, IntentFilter().apply { addAction(Intent.ACTION_SCREEN_OFF); addAction(Intent.ACTION_SCREEN_ON); addAction(Intent.ACTION_USER_PRESENT) }, ContextCompat.RECEIVER_NOT_EXPORTED)
        scope.launch { app.settings.settings.collectLatest { processor.stop(); processor.contextChanged() } }
        app.diagnostics.note("service", "Служба подключена; фильтр запрошен")
    }
    override fun onKeyEvent(event: KeyEvent): Boolean {
        val s = app.settings.settings.value
        if (BuildConfig.DEBUG) android.util.Log.d("DxButtons", "key code=${event.keyCode} scan=${event.scanCode} device=${event.device?.name} action=${event.action} repeat=${event.repeatCount} down=${event.downTime} time=${event.eventTime} canceled=${event.isCanceled}")
        app.diagnostics.change { it.copy(lastKey = "${KeyEvent.keyCodeToString(event.keyCode)} (${event.keyCode}) · scan ${event.scanCode} · ${if (event.action == 0) "DOWN" else "UP"} · repeat ${event.repeatCount}") }
        app.diagnostics.note("key", "code=${event.keyCode} scan=${event.scanCode} action=${event.action} repeat=${event.repeatCount} down=${event.downTime} time=${event.eventTime}", s.diagnostics)
        if (calibrating) {
            if (::processor.isInitialized && processor.retainedConsumption(event) == true) return true
            if (event.action == KeyEvent.ACTION_DOWN && event.repeatCount == 0) {
                calibrationDown = CalibrationDown(event.downTime, event.deviceId, event.keyCode, event.scanCode)
            } else if (event.action == KeyEvent.ACTION_UP) {
                val down = calibrationDown
                if (down != null && down.downTime == event.downTime && down.deviceId == event.deviceId && down.keyCode == event.keyCode && down.scanCode == event.scanCode && !event.isCanceled) {
                    val descriptor = event.device?.descriptor.orEmpty()
                    val binding = ButtonBinding(event.keyCode, event.scanCode, descriptor, event.device?.name.orEmpty())
                    val safe = ButtonSafety.allowed(binding) && event.device?.isVirtual == false
                    app.diagnostics.change { it.copy(candidate = if (safe) binding else null,
                        calibration = if (safe) "Получены DOWN/UP: key ${event.keyCode}, scan ${event.scanCode}, ${binding.deviceName}. Подтвердите нижнюю кнопку." else "Кнопка защищена или источник не аппаратный. STEM_PRIMARY разрешён только с отдельного Samsung Back-устройства pmic_resin; Home/Power не назначаются.") }
                    calibrationDown = null
                    calibrating = false; handler.removeCallbacks(calibrationTimeout)
                }
            }
            return false
        }
        if (!app.settings.ready.value || !::processor.isInitialized) return false
        return processor.accept(event)
    }
    override fun onAccessibilityEvent(event: AccessibilityEvent?) {
        if (event != null && ::detector.isInitialized) { detector.event(event); processor.contextChanged() }
    }
    fun calibrateButton() {
        processor.stop(); calibrating = true
        calibrationDown = null
        app.diagnostics.change { it.copy(candidate = null, calibration = "В течение 12 секунд нажмите и отпустите нижнюю кнопку. Затем вернитесь в Dx.") }
        handler.removeCallbacks(calibrationTimeout); handler.postDelayed(calibrationTimeout, 12_000)
    }
    fun calibrateFace() {
        processor.stop(); handler.removeCallbacks(faceCapture); handler.postDelayed(faceCapture, 7000)
        app.diagnostics.result(ActionResult(true, "За 7 секунд откройте основной циферблат и оставьте экран включённым."))
    }
    fun fire(gesture: Gesture) {
        val s = app.settings.settings.value
        if (!s.assigned(gesture) || !s.runWhenConnected || actionJob?.isActive == true) return
        app.diagnostics.change { it.copy(lastGesture = when(gesture) { Gesture.HOLD -> "Удержание"; Gesture.DOUBLE -> "Двойное"; Gesture.TRIPLE -> "Тройное" }) }
        if (BuildConfig.DEBUG) android.util.Log.i("DxButtons", "gesture=$gesture action=${s.gesture(gesture).action.id}")
        execute(s.gesture(gesture).action, s.gesture(gesture).haptic)
    }
    fun execute(action: Action, haptic: Boolean = false) {
        if (actionJob?.isActive == true) return
        actionJob = scope.launch {
            val platform = AndroidActionPlatform(this@DxButtonAccessibilityService, app.apps, this@DxButtonAccessibilityService)
            if (haptic && app.settings.settings.value.vibration) platform.vibrate()
            val result = ActionExecutor(platform).execute(action)
            app.diagnostics.result(result)
            if (BuildConfig.DEBUG) android.util.Log.i("DxButtons", "result success=${result.success} ${result.message}")
        }
    }
    override fun onInterrupt() { if (::processor.isInitialized) processor.stop(); app.diagnostics.note("service", "Обработка прервана") }
    override fun onUnbind(intent: Intent?): Boolean { cleanup(); return super.onUnbind(intent) }
    override fun onDestroy() { cleanup(); super.onDestroy() }
    private fun cleanup() {
        if (::processor.isInitialized) processor.stop()
        handler.removeCallbacksAndMessages(null); scope.cancel()
        calibrationDown = null
        runCatching { unregisterReceiver(screenReceiver) }
        if (instance?.get() === this) instance = null
        app.diagnostics.change { it.copy(connected = false, face = FaceVerdict(false, "Служба отключена")) }
    }
    companion object {
        private var instance: WeakReference<DxButtonAccessibilityService>? = null
        fun current() = instance?.get()
    }
}
