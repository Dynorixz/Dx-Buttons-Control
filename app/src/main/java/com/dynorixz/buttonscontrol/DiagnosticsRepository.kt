package com.dynorixz.buttonscontrol

import android.os.SystemClock
import android.content.Context
import android.os.Build
import android.util.AtomicFile
import com.dynorixz.buttonscontrol.core.*
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import java.io.File
import java.time.Instant

data class DiagnosticsState(
    val connected: Boolean = false, val enabled: Boolean = false,
    val lastKey: String = "Событий ещё нет", val lastGesture: String = "—",
    val face: FaceVerdict = FaceVerdict(false, "Служба не подключена"),
    val foreground: String = "—", val result: String = "—",
    val candidate: ButtonBinding? = null, val calibration: String = "Кнопка не определена",
    val rawSnapshot: WindowSnapshot? = null, val entries: List<DiagnosticEntry> = emptyList(),
    val keyFilter: Boolean = false, val otherFilters: Int = 0,
    val buttonStatus: String = "События нижней кнопки ещё не проверены",
    val lastButtonEvent: ButtonKeyEvent? = null,
    val lastDeviceLookup: InputDeviceLookup? = null,
    val calibrationReport: CalibrationEnvelope? = null,
    val reportIsRestored: Boolean = false
)
class DiagnosticsRepository(context: Context, private val scope: CoroutineScope) {
    val state = MutableStateFlow(DiagnosticsState())
    private val buffer = DiagnosticBuffer()
    private val reportFile = AtomicFile(File(context.filesDir, "diagnostics/button-calibration.json"))
    private val reportFileLock = Mutex()
    private var calibrationStarted = false
    init {
        scope.launch {
            val report = withContext(Dispatchers.IO) {
                reportFileLock.withLock { runCatching { CalibrationReportCodec.decode(reportFile.openRead().use { it.readBytes().toString(Charsets.UTF_8) }) }.getOrNull() }
            }
            if (report != null && !calibrationStarted) change { if (it.calibrationReport == null) it.copy(calibrationReport = report, reportIsRestored = true) else it }
        }
    }
    fun change(block: (DiagnosticsState) -> DiagnosticsState) { state.update(block) }
    fun note(kind: String, message: String, verbose: Boolean = true) {
        if (verbose) { buffer.add(DiagnosticEntry(SystemClock.uptimeMillis(), kind, message)); change { it.copy(entries = buffer.snapshot()) } }
    }
    fun result(value: ActionResult) { change { it.copy(result = value.message) }; note(if (value.success) "action" else "error", value.message) }

    fun beginCalibration() {
        calibrationStarted = true
        change { it.copy(candidate = null, calibrationReport = null, reportIsRestored = false,
            calibration = "В течение 12 секунд нажмите и отпустите нижнюю кнопку. Затем вернитесь в Dx.") }
    }

    fun recordCalibration(report: ButtonCalibrationReport) {
        val envelope = CalibrationEnvelope(BuildConfig.VERSION_NAME, Build.MANUFACTURER, Build.MODEL, Build.VERSION.SDK_INT,
            Build.VERSION.RELEASE, Build.DISPLAY, Instant.now().toString(), report)
        change { it.copy(candidate = report.candidate, calibration = report.explanation(), calibrationReport = envelope, reportIsRestored = false) }
        note("calibration", report.explanation())
        // Calibration contains only button metadata. It survives closing the UI;
        // restoring a report never restores an unconfirmed binding candidate.
        scope.launch {
            val success = withContext(Dispatchers.IO) {
                runCatching {
                    reportFileLock.withLock {
                        if (state.value.calibrationReport == envelope) {
                            reportFile.baseFile.parentFile?.mkdirs()
                            val output = reportFile.startWrite()
                            try {
                                output.write(CalibrationReportCodec.encode(envelope).toByteArray(Charsets.UTF_8))
                                reportFile.finishWrite(output)
                            } catch (e: Exception) { reportFile.failWrite(output); throw e }
                        }
                    }
                }.isSuccess
            }
            if (!success) note("error", "Не удалось сохранить отчёт калибровки; данные пока доступны на экране")
        }
    }
    fun calibrationJson() = state.value.calibrationReport?.let(CalibrationReportCodec::encode)
}
