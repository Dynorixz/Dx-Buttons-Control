package com.dynorixz.buttonscontrol.core

data class WindowSnapshot(val packageName: String, val className: String, val rootClass: String, val viewIds: List<String>, val windowId: Int, val layer: Int, val focused: Boolean, val topology: String = "")
data class FaceVerdict(val onFace: Boolean, val reason: String)
object WatchFacePolicy {
    fun evaluate(snapshot: WindowSnapshot?, signature: FaceSignature?, interactive: Boolean, keyguard: Boolean, overlay: Boolean): FaceVerdict {
        if (!interactive || keyguard) return FaceVerdict(false, "Экран выключен, AOD или блокировка")
        if (overlay) return FaceVerdict(false, "Открыто другое окно")
        if (snapshot == null) return FaceVerdict(false, "Активное окно недоступно")
        if (signature == null) return FaceVerdict(false, "Нужна калибровка циферблата")
        if (signature.viewIds.isEmpty()) return FaceVerdict(false, "Нет отличительных ID: циферблат определить нельзя")
        val matches = snapshot.packageName == signature.packageName && snapshot.className == signature.className &&
            snapshot.rootClass == signature.rootClass && signature.topology == snapshot.topology && signature.viewIds.all { it in snapshot.viewIds }
        return FaceVerdict(matches, if (matches) "Совпала подтверждённая структура окна" else "Окно отличается от циферблата")
    }
}

/** Only a bounded local list of metadata, never screen text or notification content. */
data class DiagnosticEntry(val uptimeMs: Long, val kind: String, val message: String)
class DiagnosticBuffer(private val capacity: Int = 80) {
    init { require(capacity > 0) }
    private val rows = ArrayDeque<DiagnosticEntry>()
    @Synchronized fun add(row: DiagnosticEntry) { rows.addLast(row); while(rows.size > capacity) rows.removeFirst() }
    @Synchronized fun snapshot() = rows.toList()
}
