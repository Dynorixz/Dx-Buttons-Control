package com.dynorixz.buttonscontrol.core

import org.junit.Assert.*
import org.junit.Test

class Watch8ClassicGpioProfileTest {
    private val environment = ButtonDeviceEnvironment("samsung", "SM-L505F")
    // Event and device fields were observed on SM-L505F. Descriptor is anonymized.
    private val input = InputDeviceIdentity(2, "gpio_keys", "watch8-profile-test-descriptor", 0x101, false)
    private val ready = InputDeviceLookup(DeviceLookupState.READY, input)
    private val down = ButtonKeyEvent(0, 264, 580, 2, 0x101, 30_389_063, 30_389_063)
    private val up = down.copy(action = 1, eventTime = down.eventTime + 135)

    private fun calibrate(
        env: ButtonDeviceEnvironment = environment,
        device: InputDeviceIdentity = input,
        first: ButtonKeyEvent = down,
        last: ButtonKeyEvent = up
    ): ButtonCalibrationReport {
        val lookup = ready.copy(device = device)
        return ButtonCalibration.evaluate(first, last, lookup, lookup, lookup, env)
    }

    @Test fun confirmedWatch8PairCanBeSavedAndMatchedAtRuntime() {
        val report = calibrate()
        assertTrue(report.explanation(), report.accepted)
        val settings = DxSettings(
            button = requireNotNull(report.candidate),
            hold = GestureSetting(true, Action("vibrate")),
            double = GestureSetting(true, Action("quick_actions"))
        )
        val storedSettings = SettingsCodec.decode(SettingsCodec.encode(settings))
        val binding = requireNotNull(storedSettings.button)
        assertEquals("SM-L505F", binding.deviceModel)
        assertTrue(storedSettings.assigned(Gesture.HOLD))
        assertTrue(storedSettings.assigned(Gesture.DOUBLE))
        assertTrue(ButtonSafety.matches(binding, down, ready, environment))
        assertFalse(ButtonSafety.matches(binding, down, ready, environment.copy(model = "SM-L705")))
        assertFalse(ButtonSafety.matches(binding, down, ready.copy(device = input.copy(descriptor = "another")), environment))
    }

    @Test fun onlyMeasuredWatch8ModelUsesThisProfile() {
        for (model in listOf("SM-L500", "SM-L505", "SM-L505U", "SM-L505F-FAKE")) {
            assertEquals(listOf(ButtonRejection.DEVICE_MODEL_UNVERIFIED),
                calibrate(env = environment.copy(model = model)).rejections)
        }
        assertNull(calibrate(env = environment.copy(manufacturer = "other")).candidate)
    }

    @Test fun profileCannotAllowAnotherScanSourceOrProtectedKey() {
        assertNull(calibrate(device = input.copy(isVirtual = true)).candidate)
        assertNull(calibrate(device = input.copy(descriptor = "")).candidate)
        assertNull(calibrate(device = input.copy(sources = 0x201)).candidate)
        assertNull(calibrate(first = down.copy(source = 0x201)).candidate)
        assertNull(calibrate(first = down.copy(scanCode = 116), last = up.copy(scanCode = 116)).candidate)
        for (code in listOf(3, 26)) {
            assertTrue(ButtonRejection.KEY_CODE_PROTECTED in
                calibrate(first = down.copy(keyCode = code), last = up.copy(keyCode = code)).rejections)
        }
        assertFalse(ButtonSafety.allowed(264))
    }
}
