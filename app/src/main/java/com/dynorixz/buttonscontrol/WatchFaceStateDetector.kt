package com.dynorixz.buttonscontrol

import android.accessibilityservice.AccessibilityService
import android.app.KeyguardManager
import android.os.PowerManager
import android.hardware.display.DisplayManager
import android.view.Display
import android.graphics.Rect
import android.view.accessibility.AccessibilityEvent
import android.view.accessibility.AccessibilityNodeInfo
import android.view.accessibility.AccessibilityWindowInfo
import com.dynorixz.buttonscontrol.core.*
import java.security.MessageDigest

/** Bounded metadata inspection. Never reads text, contentDescription, extras or event.source. */
class WatchFaceStateDetector(private val service: AccessibilityService, private val diagnostics: DiagnosticsRepository) {
    private val power = service.getSystemService(PowerManager::class.java)
    private val keyguard = service.getSystemService(KeyguardManager::class.java)
    private val windowClasses = mutableMapOf<Int, String>()
    private var lastIdentity: String? = null
    private var epoch = 0L
    private var previousHoldAllowed = false
    private var eventPackage: String? = null
    private var eventWindowId: Int? = null
    private var cachedSnapshot: WindowSnapshot? = null
    private var cachedOverlay = false

    fun event(event: AccessibilityEvent) {
        if (event.eventType == AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED && event.windowId >= 0) {
            eventPackage = event.packageName?.toString()
            eventWindowId = event.windowId
            if (windowClasses.size > 24) windowClasses.clear()
            event.className?.toString()?.let { windowClasses[event.windowId] = it }
        }
    }
    @Suppress("DEPRECATION")
    fun inspect(settings: DxSettings, captureFace: Boolean = false): GestureContext {
        // Global holds need screen state and window transitions, not a node-tree walk.
        // Avoid blocking the input-filter callback with many synchronous Binder reads.
        if (!captureFace && settings.face == null) {
            cachedSnapshot = null
            cachedOverlay = false
            return context(settings)
        }
        var snapshot: WindowSnapshot? = null
        var overlay = false
        try {
            val windows = service.windows.orEmpty()
            val active = windows.filter { it.type in listOf(AccessibilityWindowInfo.TYPE_APPLICATION, AccessibilityWindowInfo.TYPE_SYSTEM) && (it.isActive || it.isFocused) }.maxByOrNull { it.layer }
            if (active != null) {
                overlay = windows.any { it.id != active.id && it.layer > active.layer }
                val root = active.root
                if (root != null) {
                    val ids = linkedSetOf<String>()
                    val topology = StringBuilder()
                    var visited = 0
                    fun walk(node: AccessibilityNodeInfo, depth: Int) {
                        if (visited++ >= 120 || depth > 12) return
                        if (node.isVisibleToUser) {
                            node.viewIdResourceName?.takeIf { ids.size < 48 }?.let(ids::add)
                            val bounds = Rect(); node.getBoundsInScreen(bounds)
                            topology.append(depth).append('|').append(node.className).append('|').append(node.viewIdResourceName)
                                .append('|').append(node.childCount).append('|').append(bounds.flattenToString()).append(';')
                            for (i in 0 until minOf(node.childCount, 24)) node.getChild(i)?.let { child -> try { walk(child, depth + 1) } finally { child.recycle() } }
                        }
                    }
                    try {
                        walk(root, 0)
                        val hash = MessageDigest.getInstance("SHA-256").digest(topology.toString().toByteArray()).joinToString("") { "%02x".format(it) }
                        snapshot = WindowSnapshot(root.packageName?.toString().orEmpty(), windowClasses[active.id] ?: root.className?.toString().orEmpty(),
                            root.className?.toString().orEmpty(), ids.toList(), active.id, active.layer, active.isFocused, hash)
                    } finally { root.recycle() }
                }
            }
            windows.forEach { it.recycle() }
        } catch (_: SecurityException) { diagnostics.note("window", "Доступ к метаданным окна запрещён") }
        catch (_: IllegalStateException) { snapshot = null }
        cachedSnapshot = snapshot
        cachedOverlay = overlay
        return context(settings)
    }

    fun context(settings: DxSettings): GestureContext {
        val snapshot = cachedSnapshot?.takeIf { eventWindowId == null || eventWindowId == it.windowId }
        val displayOn = service.getSystemService(DisplayManager::class.java).getDisplay(Display.DEFAULT_DISPLAY)?.state == Display.STATE_ON
        val interactive = power.isInteractive && displayOn && !keyguard.isKeyguardLocked
        val verdict = WatchFacePolicy.evaluate(snapshot, settings.face, interactive, keyguard.isKeyguardLocked, cachedOverlay)
        val foreground = eventPackage ?: snapshot?.packageName?.takeIf { it.isNotBlank() }
        val windowId = snapshot?.windowId ?: eventWindowId
        val identity = windowId?.let { "$it:$foreground:${windowClasses[it].orEmpty()}" }
        if (identity != null && identity != lastIdentity) { epoch++; lastIdentity = identity }
        val holdAllowed = HoldAvailability.allowed(interactive, foreground, settings.excludedPackages)
        if (holdAllowed != previousHoldAllowed) { epoch++; previousHoldAllowed = holdAllowed }
        diagnostics.change { it.copy(face = verdict, foreground = foreground ?: "Неизвестно", rawSnapshot = snapshot) }
        return GestureContext(epoch, verdict.onFace, holdAllowed, interactive)
    }
}
