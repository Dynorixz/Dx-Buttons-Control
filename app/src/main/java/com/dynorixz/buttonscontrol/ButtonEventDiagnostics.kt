package com.dynorixz.buttonscontrol

import android.view.KeyEvent
import com.dynorixz.buttonscontrol.core.*

/** Copies primitive fields; the framework KeyEvent is never retained past its callback. */
fun KeyEvent.toButtonKeyEvent() = ButtonKeyEvent(action, keyCode, scanCode, deviceId, source, downTime, eventTime,
    repeatCount, isCanceled || flags and KeyEvent.FLAG_CANCELED_LONG_PRESS != 0, flags)

fun buttonDeviceDetails(event: ButtonKeyEvent?, lookup: InputDeviceLookup?): String {
    val device = lookup?.device
    fun hex(value: Int?) = value?.let { "0x${it.toUInt().toString(16)} ($it)" } ?: "неизвестно"
    return listOf(
        "keyCode: ${event?.keyCode ?: "неизвестно"}", "scanCode: ${event?.scanCode ?: "неизвестно"}",
        "deviceId: ${event?.deviceId ?: "неизвестно"}", "source: ${hex(event?.source)}",
        "lookup: ${lookup?.state ?: "нет данных"}", "name: ${device?.name ?: "не получено"}",
        "descriptor: ${device?.descriptor ?: "не получен"}", "InputDevice.id: ${device?.id ?: "неизвестно"}",
        "InputDevice.sources: ${hex(device?.sources)}", "isVirtual: ${device?.isVirtual ?: "неизвестно"}",
        "lookupError: ${lookup?.error ?: "нет"}"
    ).joinToString("\n")
}
