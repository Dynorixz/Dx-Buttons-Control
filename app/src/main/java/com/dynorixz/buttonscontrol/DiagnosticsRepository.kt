package com.dynorixz.buttonscontrol

import android.os.SystemClock
import com.dynorixz.buttonscontrol.core.*
import kotlinx.coroutines.flow.MutableStateFlow

data class DiagnosticsState(
    val connected: Boolean = false, val enabled: Boolean = false,
    val lastKey: String = "Событий ещё нет", val lastGesture: String = "—",
    val face: FaceVerdict = FaceVerdict(false, "Служба не подключена"),
    val foreground: String = "—", val result: String = "—",
    val candidate: ButtonBinding? = null, val calibration: String = "Кнопка не определена",
    val rawSnapshot: WindowSnapshot? = null, val entries: List<DiagnosticEntry> = emptyList(),
    val keyFilter: Boolean = false, val otherFilters: Int = 0,
    val buttonStatus: String = "События нижней кнопки ещё не проверены"
)
class DiagnosticsRepository {
    val state = MutableStateFlow(DiagnosticsState())
    private val buffer = DiagnosticBuffer()
    fun change(block: (DiagnosticsState) -> DiagnosticsState) { state.value = block(state.value) }
    fun note(kind: String, message: String, verbose: Boolean = true) {
        if (verbose) { buffer.add(DiagnosticEntry(SystemClock.uptimeMillis(), kind, message)); change { it.copy(entries = buffer.snapshot()) } }
    }
    fun result(value: ActionResult) { change { it.copy(result = value.message) }; note(if (value.success) "action" else "error", value.message) }
}
