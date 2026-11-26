package com.dynorixz.buttonscontrol.core

import org.junit.Assert.*
import org.junit.Test

class Watch6ClassicGpioProfileTest {
    private val environment = ButtonDeviceEnvironment("samsung", "SM-R960")
    // The photos show the model rejection. ID and descriptor are local test fixtures.
    private val input = InputDeviceIdentity(2, "gpio_keys", "watch6-test-descriptor", 0x101, false)
    private val ready = InputDeviceLookup(DeviceLookupState.READY, input)
    private val down = ButtonKeyEvent(0, 264, 580, input.id, 0x101, 100, 100)
    private val up = down.copy(action = 1, eventTime = 170)

    private fun calibrate(
        env: ButtonDeviceEnvironment = environment,
        lookup: InputDeviceLookup = ready,
        first: ButtonKeyEvent = down,
        last: ButtonKeyEvent = up
    ) = ButtonCalibration.evaluate(first, last, lookup, lookup, lookup, env)

    @Test fun watch6BindingSurvivesSavingWithAnAssignedAction() {
        val report = calibrate()
        assertTrue(report.explanation(), report.accepted)
        val settings = DxSettings(button = requireNotNull(report.candidate),
            hold = GestureSetting(true, Action("vibrate")))
        val restored = SettingsCodec.decode(SettingsCodec.encode(settings))
        val binding = requireNotNull(restored.button)
        assertEquals("SM-R960", binding.deviceModel)
        assertTrue(restored.assigned(Gesture.HOLD))
        assertTrue(ButtonSafety.matches(binding, down, ready, environment))
        assertFalse(ButtonSafety.matches(binding, down, ready, environment.copy(model = "SM-L705")))
    }

    @Test fun nearbyModelsAndOtherManufacturersRemainUnverified() {
        for (model in listOf("SM-R950", "SM-R965F", "SM-R960X", "SM-L500", "SM-L505F")) {
            assertEquals(listOf(ButtonRejection.DEVICE_MODEL_UNVERIFIED),
                calibrate(env = environment.copy(model = model)).rejections)
        }
        assertNull(calibrate(env = environment.copy(manufacturer = "other")).candidate)
    }

    @Test fun watch6DoesNotAuthorizeUnmatchedInput() {
        for (code in listOf(3, 26, 280, 281)) {
            assertTrue(ButtonRejection.KEY_CODE_PROTECTED in
                calibrate(first = down.copy(keyCode = code), last = up.copy(keyCode = code)).rejections)
        }
        assertNull(calibrate(first = down.copy(scanCode = 116), last = up.copy(scanCode = 116)).candidate)
        assertNull(calibrate(first = down.copy(source = 0x201), last = up.copy(source = 0x201)).candidate)
        assertNull(calibrate(lookup = ready.copy(device = input.copy(isVirtual = true))).candidate)
    }
}
