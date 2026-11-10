package com.dynorixz.buttonscontrol.core

import kotlinx.serialization.json.*
import org.junit.Assert.*
import org.junit.Test

class UltraGpioProfileTest {
    // The name, codes, source, id and isVirtual were supplied in customer photos.
    // The descriptor below is a test fixture, not a measurement from that watch.
    private val environment = ButtonDeviceEnvironment("samsung", "SM-L705")
    private val device = InputDeviceIdentity(2, "gpio_keys", "unmeasured-test-descriptor", 0x101, false)
    private val ready = InputDeviceLookup(DeviceLookupState.READY, device)
    private val down = ButtonKeyEvent(0, 264, 580, 2, 0x101, 100, 100)
    private val up = down.copy(action = 1, eventTime = 200)
    private fun report(env: ButtonDeviceEnvironment = environment, input: InputDeviceIdentity = device,
        first: ButtonKeyEvent = down, last: ButtonKeyEvent = up) =
        ButtonCalibration.evaluate(first, last, ready, ready, InputDeviceLookup(DeviceLookupState.READY, input), env)

    @Test fun ultraProfileAcceptsTheReportedTupleWithARealDescriptor() {
        val result = report()
        assertTrue(result.explanation(), result.accepted)
        val binding = requireNotNull(result.candidate)
        assertEquals("samsung", binding.deviceManufacturer)
        assertEquals("SM-L705", binding.deviceModel)
        assertEquals(0x101, binding.eventSource)
    }
    @Test fun standardRegionalModelSuffixIsAccepted() { assertTrue(report(environment.copy(model = "SM-L705F")).accepted) }
    @Test fun otherSamsungModelsAreNotWhitelisted() {
        for (model in listOf("SM-L355F", "SM-L700", "SM-L710F", "SM-R965F")) {
            val result = report(environment.copy(model = model))
            assertFalse(result.accepted); assertTrue(ButtonRejection.DEVICE_MODEL_UNVERIFIED in result.rejections)
        }
    }
    @Test fun aDifferentManufacturerCannotUseTheUltraException() {
        assertTrue(ButtonRejection.DEVICE_MODEL_UNVERIFIED in report(environment.copy(manufacturer = "other-brand")).rejections)
    }
    @Test fun unknownAndMarketingModelNamesFailClosed() {
        for (model in listOf("", "Galaxy Watch Ultra", "SM-L705F-other", "SM-L705XX")) assertFalse(report(environment.copy(model = model)).accepted)
    }
    @Test fun noEnvironmentDoesNotAuthorizeGenericGpioPrimaryStem() {
        assertFalse(ButtonSafety.inspect(down, ready).accepted)
        assertFalse(ButtonSafety.allowed(ButtonBinding(264, 580, "physical", "gpio_keys")))
        assertFalse(ButtonSafety.allowed(264))
    }
    @Test fun driverNameMustStillMatch() {
        assertTrue(ButtonRejection.STEM_PRIMARY_DEVICE_UNVERIFIED in report(input = device.copy(name = "pmic_pwrkey")).rejections)
        assertFalse(report(input = device.copy(name = "gpio-keys")).accepted)
    }
    @Test fun onlyReportedScan580IsAllowedForGpio() {
        for (scan in listOf(0, 114, 116, 581)) {
            val result = report(first = down.copy(scanCode = scan), last = up.copy(scanCode = scan))
            assertTrue(ButtonRejection.STEM_PRIMARY_SCAN_UNVERIFIED in result.rejections)
            assertNull(result.candidate)
        }
    }
    @Test fun homePowerSleepAndWakeRemainProtectedOnUltra() {
        for (code in listOf(3, 26, 223, 224, 280, 281, 282, 283)) {
            val result = report(first = down.copy(keyCode = code), last = up.copy(keyCode = code))
            assertTrue(ButtonRejection.KEY_CODE_PROTECTED in result.rejections)
            assertNull(result.candidate)
        }
    }
    @Test fun noDescriptorIsNotWorkedAround() { assertTrue(ButtonRejection.DESCRIPTOR_MISSING in report(input = device.copy(descriptor = "")).rejections) }
    @Test fun virtualInputIsStillRejected() {
        val result = report(input = device.copy(isVirtual = true))
        assertTrue(ButtonRejection.VIRTUAL_DEVICE in result.rejections); assertNull(result.candidate)
    }
    @Test fun eventSourceMustMatchTheConfirmedKeyboardSource() {
        for (source in listOf(0, 0x201, 0x401)) {
            val result = report(first = down.copy(source = source), last = up.copy(source = source))
            assertTrue(ButtonRejection.EVENT_SOURCE_UNVERIFIED in result.rejections)
        }
    }
    @Test fun deviceSourcesMustAlsoMatch() { assertTrue(ButtonRejection.DEVICE_SOURCE_UNVERIFIED in report(input = device.copy(sources = 0x201)).rejections) }
    @Test fun sourceCannotChangeBetweenDownAndUp() { assertTrue(ButtonRejection.PAIR_MISMATCH in report(first = down.copy(source = 0)).rejections) }
    @Test fun deviceIdMismatchStillRejectsThePair() { assertTrue(ButtonRejection.DEVICE_ID_MISMATCH in report(input = device.copy(id = 3)).rejections) }
    @Test fun aDifferentRuntimeIdAfterRebootCanMatchTheSamePhysicalBinding() {
        val binding = requireNotNull(report().candidate)
        val restarted = ready.copy(device = device.copy(id = 7))
        assertTrue(ButtonSafety.matches(binding, down.copy(deviceId = 7), restarted, environment))
    }
    @Test fun bindingAndAssignmentsSurviveSettingsRoundTrip() {
        val binding = requireNotNull(report().candidate)
        val settings = DxSettings(button = binding, hold = GestureSetting(true, Action("vibrate")),
            double = GestureSetting(true, Action("quick_actions")))
        val saved = SettingsCodec.decode(SettingsCodec.encode(settings))
        assertEquals(settings, saved)
        assertTrue(ButtonSafety.matches(requireNotNull(saved.button), down, ready, environment))
        assertTrue(saved.assigned(Gesture.HOLD)); assertTrue(saved.assigned(Gesture.DOUBLE))
    }
    @Test fun anApprovedBindingCannotRunOnAnotherModelOrDescriptor() {
        val binding = requireNotNull(report().candidate)
        assertFalse(ButtonSafety.matches(binding, down, ready, environment.copy(model = "SM-L355F")))
        assertFalse(ButtonSafety.matches(binding, down, ready, environment.copy(model = "SM-L705F")))
        assertFalse(ButtonSafety.matches(binding, down, ready.copy(device = device.copy(descriptor = "another-input")), environment))
        assertFalse(ButtonSafety.matches(binding, down, ready.copy(device = device.copy(isVirtual = true)), environment))
    }
    @Test(expected = IllegalArgumentException::class) fun gpioPrimaryCannotEnableCapture() {
        SettingsCodec.decode(SettingsCodec.encode(DxSettings(button = requireNotNull(report().candidate), mode = InputMode.CAPTURE_BACK)))
    }
    @Test fun legacyPmicProfilesAndOldSettingsDoNotRequireModelFields() {
        for (scan in listOf(114, 580)) {
            val binding = ButtonBinding(264, scan, "legacy-descriptor", "pmic_resin")
            val settings = DxSettings(button = binding)
            val encoded = SettingsCodec.json.parseToJsonElement(SettingsCodec.encode(settings)).jsonObject
            val oldBinding = JsonObject(encoded.getValue("button").jsonObject.filterKeys { it !in setOf("deviceManufacturer", "deviceModel", "eventSource") })
            val oldJson = JsonObject(encoded + ("button" to oldBinding)).toString()
            assertEquals(settings, SettingsCodec.decode(oldJson))
            val input = InputDeviceIdentity(3, "pmic_resin", "legacy-descriptor", 0, false)
            assertTrue(ButtonSafety.matches(binding, down.copy(scanCode = scan, deviceId = 3, source = 0), InputDeviceLookup(DeviceLookupState.READY, input)))
        }
    }
    @Test fun diagnosticJsonPreservesTheActualModelAndProfileContext() {
        val envelope = CalibrationEnvelope("test", "samsung", "SM-L705", 36, "16", "test-build", "2026-11-03T00:00:00Z", report())
        val restored = CalibrationReportCodec.decode(CalibrationReportCodec.encode(envelope))
        assertEquals(environment, restored.report.environment)
        assertEquals(report().candidate, restored.report.candidate)
    }
}
