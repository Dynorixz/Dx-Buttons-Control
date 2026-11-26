package com.dynorixz.buttonscontrol

import android.content.Context
import android.hardware.input.InputManager
import android.view.KeyEvent
import com.dynorixz.buttonscontrol.core.ButtonKeyEvent
import com.dynorixz.buttonscontrol.core.DeviceLookupState
import com.dynorixz.buttonscontrol.core.InputDeviceIdentity
import com.dynorixz.buttonscontrol.core.InputDeviceLookup
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

internal data class Watch8ProbeState(
    val started: Boolean = false,
    val listening: Boolean = false,
    val serviceStarted: Boolean = false,
    val appDown: ButtonKeyEvent? = null,
    val appUp: ButtonKeyEvent? = null,
    val appLookup: InputDeviceLookup? = null,
    val systemBack: Boolean = false
)

internal class Watch8ButtonProbe(private val context: Context, private val scope: CoroutineScope) {
    val state = MutableStateFlow(Watch8ProbeState())
    private var deadline: Job? = null
    private var deviceRead: Job? = null

    fun start() {
        cancel()
        val service = DxButtonAccessibilityService.current()
        state.value = Watch8ProbeState(started = true, listening = true, serviceStarted = service != null)
        service?.calibrateButton()
        deadline = scope.launch {
            // The service has its own 12 s capture timeout and may resolve InputDevice after UP.
            delay(14_000)
            state.value = state.value.copy(listening = false)
        }
    }

    fun cancel() {
        deadline?.cancel()
        deviceRead?.cancel()
        state.value = state.value.copy(listening = false)
    }

    fun recordSystemBack(): Boolean {
        val current = state.value
        if (!current.listening) return false
        state.value = current.copy(systemBack = true)
        return true
    }

    fun record(key: KeyEvent) {
        val current = state.value
        if (!current.listening || key.repeatCount != 0) return
        if (key.keyCode !in observedButtons) return
        val event = key.toButtonKeyEvent()
        when (key.action) {
            KeyEvent.ACTION_DOWN -> {
                deviceRead?.cancel()
                state.value = current.copy(appDown = event, appUp = null, appLookup = null)
            }
            KeyEvent.ACTION_UP -> state.value = current.copy(appUp = event)
            else -> return
        }
        deviceRead?.cancel()
        deviceRead = scope.launch {
            val lookup = withContext(Dispatchers.IO) {
                runCatching {
                    val device = context.getSystemService(InputManager::class.java).getInputDevice(event.deviceId)
                    if (device == null) InputDeviceLookup(DeviceLookupState.NOT_FOUND)
                    else InputDeviceLookup(DeviceLookupState.READY,
                        InputDeviceIdentity(device.id, device.name, device.descriptor, device.sources, device.isVirtual))
                }.getOrElse { InputDeviceLookup(DeviceLookupState.ERROR, error = it.javaClass.simpleName) }
            }
            state.value = state.value.copy(appLookup = lookup)
        }
    }

    companion object {
        private val observedButtons = setOf(KeyEvent.KEYCODE_BACK, KeyEvent.KEYCODE_STEM_PRIMARY,
            KeyEvent.KEYCODE_STEM_1, KeyEvent.KEYCODE_STEM_2, KeyEvent.KEYCODE_STEM_3)
    }
}
