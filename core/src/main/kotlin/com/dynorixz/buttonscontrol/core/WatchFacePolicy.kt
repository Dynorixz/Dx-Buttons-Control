package com.dynorixz.buttonscontrol.core

data class WindowSnapshot(val packageName: String, val className: String, val rootClass: String, val viewIds: List<String>, val windowId: Int, val layer: Int, val focused: Boolean, val topology: String = "", val trustedHome: Boolean = false, val faceSurfaceVisible: Boolean = false)
data class FaceVerdict(val onFace: Boolean, val reason: String)
object WatchFacePolicy {
    private val hostAnchors = setOf("item_watchface", "watchface_overlay", "koru_surfaceview_container", "watch_face_container", "watchface_container")
    private val negativeMarkers = listOf("recent_list", "recent_item", "app_drawer", "apps_grid", "quick_settings", "quick_panel", "notification_list", "notification_stack", "tile_pager", "tile_container")
    private fun localName(id: String) = id.substringAfterLast('/').lowercase()
    fun rejected(packageName: String, ids: List<String>): Boolean =
        packageName.endsWith(".recent") || packageName.contains(".settings") || ids.any { id -> negativeMarkers.any { localName(id).contains(it) } }
    fun anchors(ids: List<String>): List<String> = ids.filter { id ->
        val name = localName(id)
        !id.startsWith("android:") && (name in hostAnchors || name in setOf("face", "clock_face", "dial_face") || name.contains("watchface") || name.contains("watch_face"))
    }.distinct().sorted()
    fun canCalibrate(snapshot: WindowSnapshot): Boolean = !rejected(snapshot.packageName, snapshot.viewIds) && anchors(snapshot.viewIds).isNotEmpty()
    fun evaluate(snapshot: WindowSnapshot?, signature: FaceSignature?, interactive: Boolean, keyguard: Boolean, overlay: Boolean): FaceVerdict {
        if (!interactive || keyguard) return FaceVerdict(false, "Экран выключен, AOD или блокировка")
        if (overlay) return FaceVerdict(false, "Открыто другое окно")
        if (snapshot == null) return FaceVerdict(false, "Активное окно недоступно")
        if (rejected(snapshot.packageName, snapshot.viewIds)) return FaceVerdict(false, "Открыто меню часов")
        val hostCount = snapshot.viewIds.map(::localName).count { it in hostAnchors }
        if (snapshot.trustedHome && snapshot.faceSurfaceVisible && hostCount >= 2) return FaceVerdict(true, "Главный циферблат")
        if (signature == null) return FaceVerdict(false, "Настройте циферблат")
        val anchors = anchors(signature.viewIds)
        if (rejected(signature.packageName, signature.viewIds) || anchors.isEmpty()) return FaceVerdict(false, "Повторите настройку циферблата")
        val matches = snapshot.packageName == signature.packageName && snapshot.className == signature.className &&
            snapshot.rootClass == signature.rootClass && anchors.all { it in snapshot.viewIds } && (!snapshot.trustedHome || snapshot.faceSurfaceVisible)
        return FaceVerdict(matches, if (matches) "Главный циферблат" else "Открыт другой экран")
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
