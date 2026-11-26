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
    private val buttonEnvironment = ButtonDeviceEnvironment(android.os.Build.MANUFACTURER, android.os.Build.MODEL)
    private lateinit var detector: WatchFaceStateDetector
    private lateinit var processor: ButtonEventProcessor
    private var calibrating = false
    private var calibrationDown: ButtonKeyEvent? = null
    private var calibrationDownLookup: InputDeviceLookup? = null
    private var calibrationSession = 0L
    private var calibrationResolve: Job? = null
    private var actionJob: Job? = null
    private lateinit var devices: HardwareInputs
    private val calibrationTimeout = Runnable {
        calibrating = false
        val down = calibrationDown
        val lookup = down?.let { devices.lookup(it.deviceId) } ?: InputDeviceLookup(DeviceLookupState.NOT_FOUND)
        publishCalibration(ButtonCalibration.evaluate(down, null, calibrationDownLookup, null, lookup, buttonEnvironment))
        calibrationDown = null
        calibrationDownLookup = null
    }
    private var windowRefresh: Job? = null
    private var refreshRequested = false
    private var mainProbe: Job? = null
    private var lastWindowStatus = ""
    private var faceCapture: Job? = null
    private val displayListener = object : android.hardware.display.DisplayManager.DisplayListener {
        override fun onDisplayAdded(displayId: Int) = Unit
        override fun onDisplayRemoved(displayId: Int) = Unit
        override fun onDisplayChanged(displayId: Int) {
            if (::detector.isInitialized && displayId == android.view.Display.DEFAULT_DISPLAY) refreshWindow()
        }
    }
    private fun refreshWindow() {
        refreshRequested = true
        if (windowRefresh?.isActive == true) return
        if (BuildConfig.DEBUG && mainProbe?.isActive != true) mainProbe = scope.launch(Dispatchers.IO) {
            delay(750)
            val stack = Looper.getMainLooper().thread.stackTrace
            if (stack.none { it.className == "android.os.MessageQueue" && it.methodName == "nativePollOnce" })
                android.util.Log.w("DxButtons", "main-thread probe\n${stack.take(24).joinToString("\n")}")
        }
        windowRefresh = scope.launch {
            do {
                refreshRequested = false
                delay(40)
                try {
                    detector.updateScreenState()
                    val context = detector.refresh(app.settings.settings.value)
                    processor.contextChanged()
                    val d = app.diagnostics.state.value
                    val w = d.rawSnapshot
                    val status = "${d.foreground}:${w?.windowId}:${context.onFace}:${w?.trustedHome}:${w?.faceSurfaceVisible}:${d.face.reason}"
                    if (BuildConfig.DEBUG && status != lastWindowStatus) android.util.Log.d("DxButtons", "window=$status ids=${w?.viewIds}")
                    lastWindowStatus = status
                }
                catch (e: CancellationException) { throw e }
                catch (_: Exception) { app.diagnostics.note("window", "Не удалось проверить окно") }
            } while (refreshRequested)
        }
    }
    private val screenReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            if (intent?.action == Intent.ACTION_SCREEN_OFF) detector.screenOff()
            processor.contextChanged(); refreshWindow()
        }
    }
    override fun onServiceConnected() {
        super.onServiceConnected()
        instance = WeakReference(this)
        serviceInfo = serviceInfo.apply { flags = flags or AccessibilityServiceInfo.FLAG_REQUEST_FILTER_KEY_EVENTS or AccessibilityServiceInfo.FLAG_RETRIEVE_INTERACTIVE_WINDOWS or AccessibilityServiceInfo.FLAG_REPORT_VIEW_IDS }
        // Android otherwise refreshes prefetched nodes on its callback looper, even
        // for content events this service did not subscribe to. Watch complications
        // can then stall physical-key delivery for multiple interaction timeouts.
        if (android.os.Build.VERSION.SDK_INT >= 33) {
            val disabled = setCacheEnabled(false)
            if (BuildConfig.DEBUG) android.util.Log.d("DxButtons", "accessibility node cache disabled=$disabled")
        }
        devices = HardwareInputs(this, scope, handler)
        detector = WatchFaceStateDetector(this, app.diagnostics)
        getSystemService(android.hardware.display.DisplayManager::class.java).registerDisplayListener(displayListener, handler)
        processor = ButtonEventProcessor(handler, detector, { app.settings.settings.value }, ::fire,
            { handler.post { performGlobalAction(GLOBAL_ACTION_BACK) } }, { devices.lookup(it) }, environment = buttonEnvironment) { status ->
            app.diagnostics.change { it.copy(buttonStatus = status) }
            if (BuildConfig.DEBUG) android.util.Log.d("DxButtons", status)
        }
        app.diagnostics.change { it.copy(connected = true, enabled = true,
            keyFilter = serviceInfo.capabilities and AccessibilityServiceInfo.CAPABILITY_CAN_REQUEST_FILTER_KEY_EVENTS != 0,
            otherFilters = CompatibilityChecker(this).competingFilters()) }
        ContextCompat.registerReceiver(this, screenReceiver, IntentFilter().apply { addAction(Intent.ACTION_SCREEN_OFF); addAction(Intent.ACTION_SCREEN_ON); addAction(Intent.ACTION_USER_PRESENT) }, ContextCompat.RECEIVER_NOT_EXPORTED)
        scope.launch { app.settings.settings.collectLatest { processor.stop(); processor.contextChanged(); refreshWindow() } }
        app.diagnostics.note("service", "Служба подключена; фильтр запрошен")
    }
    override fun onKeyEvent(event: KeyEvent): Boolean {
        val received = android.os.SystemClock.uptimeMillis()
        if (BuildConfig.DEBUG) android.util.Log.d("DxButtons", "key-entry age=${received-event.eventTime}ms")
        try { return handleKey(event) }
        finally { if (BuildConfig.DEBUG) android.util.Log.d("DxButtons", "key-exit duration=${android.os.SystemClock.uptimeMillis()-received}ms") }
    }
    private fun handleKey(event: KeyEvent): Boolean {
        val lookup = if (::devices.isInitialized) devices.lookup(event.deviceId) else InputDeviceLookup(DeviceLookupState.PENDING)
        val device = lookup.device
        val sample = event.toButtonKeyEvent()
        val s = app.settings.settings.value
        if (BuildConfig.DEBUG) android.util.Log.d("DxButtons", "key code=${event.keyCode} scan=${event.scanCode} device=${device?.name} action=${event.action} repeat=${event.repeatCount} down=${event.downTime} time=${event.eventTime} canceled=${event.isCanceled}")
        app.diagnostics.change { it.copy(lastKey = "${KeyEvent.keyCodeToString(event.keyCode)} (${event.keyCode}) · scan ${event.scanCode} · ${if (event.action == 0) "DOWN" else "UP"} · repeat ${event.repeatCount}",
            lastButtonEvent = sample, lastDeviceLookup = lookup) }
        app.diagnostics.note("key", "code=${event.keyCode} scan=${event.scanCode} action=${event.action} repeat=${event.repeatCount} down=${event.downTime} time=${event.eventTime}", s.diagnostics)
        if (calibrating) {
            if (::processor.isInitialized && processor.retainedConsumption(event) == true) return true
            // Every diagnostic calibration is visible in release logcat as well.
            // Only key/device metadata are logged; no screen content is inspected.
            android.util.Log.i("DxButtonCalibration", "event=${sample.action} down=${sample.downTime} time=${sample.eventTime} repeat=${sample.repeatCount}\n${buttonDeviceDetails(sample, lookup)}")
            if (calibrationResolve?.isActive == true) return false
            if (event.action == KeyEvent.ACTION_DOWN && event.repeatCount == 0) {
                calibrationDown = sample
                calibrationDownLookup = lookup
            } else if (event.action == KeyEvent.ACTION_UP) {
                val down = calibrationDown
                val downLookup = calibrationDownLookup
                val session = calibrationSession
                handler.removeCallbacks(calibrationTimeout)
                app.diagnostics.change { it.copy(calibration = "Пара событий получена. Проверяем InputDevice…") }
                calibrationResolve = scope.launch {
                    // Resolving after UP avoids a Binder call inside onKeyEvent and
                    // prevents a cold cache from being mistaken for isVirtual=true.
                    val resolved = devices.resolve(sample.deviceId)
                    if (session != calibrationSession) return@launch
                    publishCalibration(ButtonCalibration.evaluate(down, sample, downLookup, lookup, resolved, buttonEnvironment))
                    calibrationDown = null
                    calibrationDownLookup = null
                    calibrating = false
                }
            }
            return false
        }
        if (!app.settings.ready.value || !::processor.isInitialized) return false
        return processor.accept(event)
    }
    override fun onAccessibilityEvent(event: AccessibilityEvent?) {
        if (event != null && ::detector.isInitialized) { detector.event(event); processor.contextChanged(); refreshWindow() }
    }
    fun calibrateButton() {
        calibrationSession++
        calibrationResolve?.cancel(); calibrationResolve = null
        processor.stop(); calibrating = true
        calibrationDown = null
        calibrationDownLookup = null
        app.diagnostics.beginCalibration()
        handler.removeCallbacks(calibrationTimeout); handler.postDelayed(calibrationTimeout, 12_000)
    }
    private fun publishCalibration(report: ButtonCalibrationReport) {
        app.diagnostics.recordCalibration(report)
        val event = report.up ?: report.down
        app.diagnostics.change { if (it.lastButtonEvent == event && event != null) it.copy(lastDeviceLookup = report.resolvedLookup) else it }
        android.util.Log.i("DxButtonCalibration", "accepted=${report.accepted} bindingAllowed=${report.verdict.bindingAllowed} hardwareAllowed=${report.verdict.hardwareAllowed}\n${report.explanation()}\n${buttonDeviceDetails(event, report.resolvedLookup)}")
    }
    fun calibrateFace() {
        faceCapture?.cancel()
        processor.stop()
        faceCapture = scope.launch {
            if (!performGlobalAction(GLOBAL_ACTION_HOME)) {
                app.diagnostics.result(ActionResult(false, "Откройте циферблат и повторите настройку")); return@launch
            }
            app.diagnostics.result(ActionResult(true, "Проверяем циферблат…"))
            delay(900)
            try {
                detector.updateScreenState()
                detector.refresh(app.settings.settings.value, captureFace = true)
                val window = app.diagnostics.state.value.rawSnapshot
                if (window == null || window.packageName == packageName || !WatchFacePolicy.canCalibrate(window) || !detector.context(app.settings.settings.value).interactive) {
                    app.diagnostics.result(ActionResult(false, "Это не циферблат. Откройте главный экран часов и повторите."))
                } else {
                    val signature = FaceSignature(window.packageName, window.className, window.rootClass, WatchFacePolicy.anchors(window.viewIds).take(48), window.topology)
                    app.settings.update { it.copy(face = signature) }
                    app.diagnostics.result(ActionResult(true, "Циферблат настроен"))
                    android.widget.Toast.makeText(this@DxButtonAccessibilityService, "Циферблат настроен", android.widget.Toast.LENGTH_SHORT).show()
                }
            } catch (e: CancellationException) { throw e }
            catch (_: Exception) { app.diagnostics.result(ActionResult(false, "Не удалось проверить циферблат")) }
        }
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
        calibrationDownLookup = null
        calibrationSession++
        if (::devices.isInitialized) devices.close()
        getSystemService(android.hardware.display.DisplayManager::class.java).unregisterDisplayListener(displayListener)
        runCatching { unregisterReceiver(screenReceiver) }
        if (instance?.get() === this) instance = null
        app.diagnostics.change { it.copy(connected = false, face = FaceVerdict(false, "Служба отключена")) }
    }
    companion object {
        private var instance: WeakReference<DxButtonAccessibilityService>? = null
        fun current() = instance?.get()
    }
}
