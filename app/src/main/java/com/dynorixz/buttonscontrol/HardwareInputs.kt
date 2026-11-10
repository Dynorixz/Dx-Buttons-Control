package com.dynorixz.buttonscontrol

import android.content.Context
import android.hardware.input.InputManager
import android.os.Handler
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import com.dynorixz.buttonscontrol.core.*

typealias HardwareInput = InputDeviceIdentity

/** onKeyEvent must not call InputManager while Android is waiting for the filter result. */
class HardwareInputs(context: Context, private val scope: CoroutineScope, handler: Handler) : InputManager.InputDeviceListener {
    private val manager = context.getSystemService(InputManager::class.java)
    private val cache = InputDeviceCache(scope, { id ->
        manager.getInputDevice(id)?.let { InputDeviceIdentity(it.id, it.name, it.descriptor, it.sources, it.isVirtual) }
    })
    init {
        manager.registerInputDeviceListener(this, handler)
        scope.launch(Dispatchers.IO) { runCatching { manager.inputDeviceIds }.getOrDefault(intArrayOf()).forEach { cache.lookup(it) } }
    }
    fun lookup(id: Int) = cache.lookup(id)
    fun get(id: Int): HardwareInput? = lookup(id).takeIf { it.state == DeviceLookupState.READY }?.device
    suspend fun resolve(id: Int): InputDeviceLookup {
        if (cache.lookup(id).state != DeviceLookupState.PENDING) cache.refresh(id)
        return cache.resolve(id)
    }
    override fun onInputDeviceAdded(deviceId: Int) = cache.refresh(deviceId)
    override fun onInputDeviceChanged(deviceId: Int) = cache.refresh(deviceId)
    override fun onInputDeviceRemoved(deviceId: Int) = cache.removed(deviceId)
    fun close() { manager.unregisterInputDeviceListener(this); cache.close() }
}
