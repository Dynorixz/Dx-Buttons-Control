package com.dynorixz.buttonscontrol.core

/** Event identities are hints; a fresh active-window measurement is authoritative.
 * A suspended measurement cannot publish over a more recent window/screen event.
 */
class WindowStateTracker {
    var revision = 0L
        private set
    var snapshot: WindowSnapshot? = null
        private set
    var overlay = false
        private set
    private var eventWindowId: Int? = null
    private var eventPackage: String? = null
    val windowId get() = snapshot?.windowId ?: eventWindowId
    val foreground get() = snapshot?.packageName ?: eventPackage

    fun event(windowId: Int, packageName: String?, stateChanged: Boolean, activeChanged: Boolean = false) {
        revision++
        if (stateChanged && windowId >= 0) {
            if (eventWindowId != windowId || eventPackage != packageName) snapshot = null
            eventWindowId = windowId
            eventPackage = packageName
        }
        if (activeChanged) snapshot = null
    }

    fun publish(requested: Long, value: WindowSnapshot?, blocked: Boolean): Boolean {
        if (requested != revision) return false
        snapshot = value
        overlay = blocked
        value?.let { eventWindowId = it.windowId; eventPackage = it.packageName }
        return true
    }

    fun invalidate() { revision++; snapshot = null; overlay = false }
}
