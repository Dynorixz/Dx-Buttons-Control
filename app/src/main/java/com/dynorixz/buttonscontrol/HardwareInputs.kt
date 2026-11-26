package com.dynorixz.buttonscontrol

import android.content.Context
import android.hardware.input.InputManager
import android.os.Handler
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import java.util.concurrent.ConcurrentHashMap

data class HardwareInput(val name: String, val descriptor: String, val virtual: Boolean)

/** onKeyEvent must not call InputManager while Android is waiting for the filter result. */
class HardwareInputs(context: Context, private val scope: CoroutineScope, handler: Handler) : InputManager.InputDeviceListener {
    private val manager = context.getSystemService(InputManager::class.java)
    private val values = ConcurrentHashMap<Int, HardwareInput>()
    private val pending = ConcurrentHashMap.newKeySet<Int>()
    init {
        manager.registerInputDeviceListener(this, handler)
        scope.launch(Dispatchers.IO) { manager.inputDeviceIds.forEach(::refresh) }
    }
    fun get(id: Int): HardwareInput? {
        val value = values[id]
        if (value == null) refresh(id)
        return value
    }
    private fun refresh(id: Int) {
        if (!pending.add(id)) return
        scope.launch(Dispatchers.IO) {
            try { manager.getInputDevice(id)?.let { values[id] = HardwareInput(it.name, it.descriptor, it.isVirtual) } }
            finally { pending.remove(id) }
        }
    }
    override fun onInputDeviceAdded(deviceId: Int) = refresh(deviceId)
    override fun onInputDeviceChanged(deviceId: Int) { values.remove(deviceId); refresh(deviceId) }
    override fun onInputDeviceRemoved(deviceId: Int) { values.remove(deviceId) }
    fun close() { manager.unregisterInputDeviceListener(this); values.clear() }
}
