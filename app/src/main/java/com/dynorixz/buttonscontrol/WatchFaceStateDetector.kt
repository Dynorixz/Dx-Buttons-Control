package com.dynorixz.buttonscontrol

import android.accessibilityservice.AccessibilityService
import android.app.KeyguardManager
import android.content.Intent
import android.content.pm.ApplicationInfo
import android.content.pm.PackageManager
import android.graphics.Rect
import android.hardware.display.DisplayManager
import android.os.PowerManager
import android.view.Display
import android.view.accessibility.AccessibilityEvent
import android.view.accessibility.AccessibilityNodeInfo
import android.view.accessibility.AccessibilityWindowInfo
import com.dynorixz.buttonscontrol.core.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.withContext
import java.security.MessageDigest

/** Reads only window identity, visible resource IDs and bounds. Never reads text or descriptions. */
class WatchFaceStateDetector(private val service: AccessibilityService, private val diagnostics: DiagnosticsRepository) {
    private val power = service.getSystemService(PowerManager::class.java)
    private val keyguard = service.getSystemService(KeyguardManager::class.java)
    private val display = service.getSystemService(DisplayManager::class.java)
    private val windowClasses = mutableMapOf<Int, String>()
    private val windowPackages = mutableMapOf<Int, String>()
    private val windowsState = WindowStateTracker()
    private var epoch = 0L
    private var lastIdentity: String? = null
    private var interactive = false
    private var screenRevision = 0L
    fun screenOff() {
        screenRevision++
        interactive = false
        windowsState.invalidate()
    }
    suspend fun updateScreenState(): Boolean {
        val requested = screenRevision
        // Display/InputManager and system-server locks can wait for the key filter.
        // No Binder query may block the main looper that must return that filter result.
        val value = withContext(Dispatchers.IO) {
            power.isInteractive && display.getDisplay(Display.DEFAULT_DISPLAY)?.state == Display.STATE_ON && !keyguard.isKeyguardLocked
        }
        if (requested != screenRevision) return false
        val changed = value != interactive
        interactive = value
        return changed
    }
    @Suppress("DEPRECATION")
    private val homePackages by lazy {
        service.packageManager.queryIntentActivities(Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_HOME), PackageManager.MATCH_DEFAULT_ONLY)
            .mapNotNull { it.activityInfo?.takeIf { a -> a.applicationInfo.flags and ApplicationInfo.FLAG_SYSTEM != 0 }?.packageName }.toSet()
    }

    fun event(event: AccessibilityEvent) {
        val stateChanged = event.eventType == AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED
        val activeChanged = event.eventType == AccessibilityEvent.TYPE_WINDOWS_CHANGED &&
            event.windowChanges and (AccessibilityEvent.WINDOWS_CHANGE_ACTIVE or AccessibilityEvent.WINDOWS_CHANGE_FOCUSED or AccessibilityEvent.WINDOWS_CHANGE_REMOVED or AccessibilityEvent.WINDOWS_CHANGE_ADDED) != 0
        windowsState.event(event.windowId, event.packageName?.toString(), stateChanged, activeChanged)
        if (event.eventType == AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED && event.windowId >= 0) {
            val pkg = event.packageName?.toString()
            if (windowClasses.size > 24) { windowClasses.clear(); windowPackages.clear() }
            pkg?.let { windowPackages[event.windowId] = it }
            event.className?.toString()?.let { windowClasses[event.windowId] = it }
        }
    }

    @Suppress("DEPRECATION")
    suspend fun refresh(settings: DxSettings, captureFace: Boolean = false): GestureContext {
        if (!captureFace && settings.face == null && !settings.assigned(Gesture.DOUBLE) && !settings.assigned(Gesture.TRIPLE)) {
            windowsState.invalidate()
            return context(settings)
        }
        val requested = windowsState.revision
        val classes = windowClasses.toMap()
        val packages = windowPackages.toMap()
        val measured = withContext(Dispatchers.IO) {
            val work = currentCoroutineContext()
            var result: WindowSnapshot? = null
            var blocked = false
            val windows = runCatching { service.windows.orEmpty() }.getOrDefault(emptyList())
            try {
                val active = windows.filter { it.type in listOf(AccessibilityWindowInfo.TYPE_APPLICATION, AccessibilityWindowInfo.TYPE_SYSTEM) && (it.isActive || it.isFocused) }.maxByOrNull { it.layer }
                if (active != null) {
                    val screen = Rect(0, 0, service.resources.displayMetrics.widthPixels, service.resources.displayMetrics.heightPixels)
                    fun covers(bounds: Rect, fraction: Double): Boolean {
                        val intersection = Rect(bounds)
                        return intersection.intersect(screen) && intersection.width().toLong() * intersection.height() >= screen.width().toLong() * screen.height() * fraction
                    }
                    blocked = windows.any { w ->
                        val bounds = Rect(); w.getBoundsInScreen(bounds)
                        w.id != active.id && w.layer > active.layer && covers(bounds, 0.5)
                    }
                    val knownPackage = packages[active.id]
                    if (knownPackage != null && !pkgAllowed(knownPackage, settings, captureFace)) {
                        // Even reading only root.packageName builds an accessibility
                        // tree in the target app. Never ask Compose or other apps to
                        // construct a tree we do not need for face recognition.
                        result = WindowSnapshot(knownPackage, classes[active.id].orEmpty(), "", emptyList(), active.id, active.layer, active.isFocused)
                    } else (if (android.os.Build.VERSION.SDK_INT >= 33) active.getRoot(0) else active.root)?.let { root ->
                        try {
                            val ids = linkedSetOf<String>()
                            val shouldInspect = pkgAllowed(root.packageName?.toString().orEmpty(), settings, captureFace)
                            var visited = 0
                            var surfaceVisible = false
                            fun walk(node: AccessibilityNodeInfo, depth: Int) {
                                work.ensureActive()
                                if (visited++ >= 32 || depth > 6 || !node.isVisibleToUser) return
                                val bounds = Rect(); node.getBoundsInScreen(bounds)
                                if (!Rect.intersects(bounds, screen)) return
                                node.viewIdResourceName?.let { id ->
                                    if (ids.size < 80) ids.add(id)
                                    if (id.substringAfterLast('/') in setOf("item_watchface", "watchface_overlay", "watch_face_container", "watchface_container") && covers(bounds, 0.85)) surfaceVisible = true
                                }
                                if (node.viewIdResourceName?.substringAfterLast('/') == "item_watchface") return
                                for (i in 0 until minOf(node.childCount, 12)) node.getChild(i)?.let { child ->
                                    try { walk(child, depth + 1) } finally { child.recycle() }
                                }
                            }
                            if (shouldInspect) walk(root, 0)
                            val pkg = root.packageName?.toString().orEmpty()
                            val fingerprint = (listOf(pkg, root.className.toString()) + ids.sorted()).joinToString("|")
                            val hash = MessageDigest.getInstance("SHA-256").digest(fingerprint.toByteArray()).joinToString("") { "%02x".format(it) }
                            result = WindowSnapshot(pkg, classes[active.id] ?: root.className.toString(), root.className.toString(), ids.toList(), active.id, active.layer, active.isFocused, hash, pkg in homePackages, surfaceVisible)
                        } finally { root.recycle() }
                    }
                }
            } finally { windows.forEach { it.recycle() } }
            result to blocked
        }
        windowsState.publish(requested, measured.first, measured.second)
        return context(settings)
    }

    private fun pkgAllowed(pkg: String, settings: DxSettings, capture: Boolean) =
        capture || pkg in homePackages || settings.face?.let { pkg == it.packageName && !WatchFacePolicy.rejected(pkg, it.viewIds) } == true

    fun context(settings: DxSettings): GestureContext {
        val current = windowsState.snapshot
        val face = WatchFacePolicy.evaluate(current, settings.face, interactive, !interactive, windowsState.overlay)
        val foreground = windowsState.foreground
        val identity = "${windowsState.windowId}:$foreground"
        if (identity != lastIdentity) { lastIdentity = identity; epoch++ }
        diagnostics.change { it.copy(face = face, foreground = foreground ?: "Неизвестно", rawSnapshot = current) }
        return GestureContext(epoch, face.onFace, HoldAvailability.allowed(interactive, foreground, settings.excludedPackages), interactive)
    }
}
