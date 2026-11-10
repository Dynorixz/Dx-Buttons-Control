package com.dynorixz.buttonscontrol.core

import org.junit.Assert.*
import org.junit.Test

class ButtonCalibrationTest {
    private val device = InputDeviceIdentity(8, "pmic_resin", "physical-descriptor", 0x101, false)
    private val ready = InputDeviceLookup(DeviceLookupState.READY, device)
    private val pending = InputDeviceLookup(DeviceLookupState.PENDING)
    private val down = ButtonKeyEvent(0, 264, 580, 8, 0x101, 100, 100)
    private val up = down.copy(action = 1, eventTime = 230)
    private fun evaluate(lookup: InputDeviceLookup = ready, first: ButtonKeyEvent? = down, last: ButtonKeyEvent? = up) =
        ButtonCalibration.evaluate(first, last, pending, pending, lookup)

    @Test fun existingSamsungBackRemainsSupported() {
        val report = evaluate()
        assertTrue(report.accepted)
        assertEquals(ButtonBinding(264, 580, "physical-descriptor", "pmic_resin"), report.candidate)
        assertTrue(report.verdict.bindingAllowed == true)
        assertTrue(report.verdict.hardwareAllowed == true)
    }
    @Test fun unknownUltraDeviceNameIsExplainedWithoutWhitelistingIt() {
        val report = evaluate(ready.copy(device = device.copy(name = "unverified_ultra_driver")))
        assertEquals(listOf(ButtonRejection.STEM_PRIMARY_DEVICE_UNVERIFIED), report.rejections)
        assertFalse(report.accepted)
        assertNull(report.candidate)
        assertEquals(false, report.verdict.bindingAllowed)
        assertEquals(true, report.verdict.hardwareAllowed)
    }
    @Test fun virtualSourceRejectionIsSeparateFromTheBindingRule() {
        val report = evaluate(ready.copy(device = device.copy(isVirtual = true)))
        assertEquals(listOf(ButtonRejection.VIRTUAL_DEVICE), report.rejections)
        assertEquals(true, report.verdict.bindingAllowed)
        assertEquals(false, report.verdict.hardwareAllowed)
        assertNull(report.candidate)
    }
    @Test fun pendingInputIsNotReportedAsVirtualOrAsAnUnknownDriver() {
        val report = evaluate(pending)
        assertEquals(listOf(ButtonRejection.DEVICE_INFO_PENDING), report.rejections)
        assertNull(report.verdict.bindingAllowed)
        assertNull(report.verdict.hardwareAllowed)
        assertNull(report.candidate)
    }
    @Test fun missingDeviceHasItsOwnReason() { assertEquals(listOf(ButtonRejection.DEVICE_NOT_FOUND), evaluate(InputDeviceLookup(DeviceLookupState.NOT_FOUND)).rejections) }
    @Test fun failedLookupIsNotTreatedAsVirtual() { assertEquals(listOf(ButtonRejection.DEVICE_INFO_READ_FAILED), evaluate(InputDeviceLookup(DeviceLookupState.ERROR, error = "SecurityException")).rejections) }
    @Test fun disconnectedDeviceIsRejected() { assertEquals(listOf(ButtonRejection.DEVICE_REMOVED), evaluate(InputDeviceLookup(DeviceLookupState.REMOVED)).rejections) }
    @Test fun emptyDescriptorIsExplained() { assertEquals(listOf(ButtonRejection.DESCRIPTOR_MISSING), evaluate(ready.copy(device = device.copy(descriptor = ""))).rejections) }
    @Test fun deviceIdMismatchCannotBeBound() { assertEquals(listOf(ButtonRejection.DEVICE_ID_MISMATCH), evaluate(ready.copy(device = device.copy(id = 10))).rejections) }
    @Test fun allApplicableFailuresAreReported() {
        val result = evaluate(ready.copy(device = device.copy(name = "pmic_pwrkey", descriptor = "", isVirtual = true)))
        assertEquals(setOf(ButtonRejection.DESCRIPTOR_MISSING, ButtonRejection.STEM_PRIMARY_DEVICE_UNVERIFIED, ButtonRejection.VIRTUAL_DEVICE), result.rejections.toSet())
    }
    @Test fun homeAndPowerNeverBecomeCandidates() {
        for (code in listOf(3, 26, 223, 224, 280, 281, 282, 283)) {
            val result = evaluate(first = down.copy(keyCode = code), last = up.copy(keyCode = code))
            assertFalse(result.accepted); assertTrue(ButtonRejection.KEY_CODE_PROTECTED in result.rejections)
        }
    }
    @Test fun primaryPowerDriverRemainsRejected() { assertFalse(evaluate(ready.copy(device = device.copy(name = "pmic_pwrkey"))).accepted) }
    @Test fun unverifiedPrimaryScanRemainsRejected() { assertTrue(ButtonRejection.STEM_PRIMARY_SCAN_UNVERIFIED in evaluate(first = down.copy(scanCode = 116), last = up.copy(scanCode = 116)).rejections) }
    @Test fun supportedAlternateScanRemainsAccepted() { assertTrue(evaluate(first = down.copy(scanCode = 114), last = up.copy(scanCode = 114)).accepted) }
    @Test fun standardBackAndStemKeysKeepWorking() {
        for (code in listOf(4, 265, 266, 267)) assertTrue(evaluate(ready.copy(device = device.copy(name = "other-physical-input")), down.copy(keyCode = code), up.copy(keyCode = code)).accepted)
    }
    @Test fun absentDownCannotProduceABinding() { assertTrue(ButtonRejection.DOWN_MISSING in evaluate(first = null).rejections); assertNull(evaluate(first = null).candidate) }
    @Test fun lostUpCannotProduceABinding() { assertTrue(ButtonRejection.UP_MISSING in evaluate(last = null).rejections); assertNull(evaluate(last = null).candidate) }
    @Test fun canceledPairCannotProduceABinding() { assertTrue(ButtonRejection.EVENT_CANCELED in evaluate(last = up.copy(canceled = true)).rejections); assertNull(evaluate(last = up.copy(canceled = true)).candidate) }
    @Test fun pairMustHaveMatchingHardwareIdentity() {
        for (other in listOf(up.copy(downTime = 99), up.copy(deviceId = 9), up.copy(scanCode = 114), up.copy(keyCode = 4))) {
            val result = evaluate(last = other)
            assertTrue(ButtonRejection.PAIR_MISMATCH in result.rejections); assertNull(result.candidate)
        }
    }
    @Test fun malformedEventTimesAndRepeatsCannotBeCalibrated() {
        assertTrue(ButtonRejection.INVALID_SEQUENCE in evaluate(last = up.copy(eventTime = 90)).rejections)
        assertTrue(ButtonRejection.INVALID_SEQUENCE in evaluate(first = down.copy(repeatCount = 1)).rejections)
    }
    @Test fun aValidatedCandidateCanBeSavedWithActionsAndMatchedAtRuntime() {
        val binding = requireNotNull(evaluate().candidate)
        val settings = DxSettings(button = binding, hold = GestureSetting(true, Action("vibrate")))
        val saved = SettingsCodec.decode(SettingsCodec.encode(settings))
        assertTrue(saved.assigned(Gesture.HOLD))
        assertTrue(ButtonSafety.matches(requireNotNull(saved.button), down, ready))
    }
    @Test fun savedBindingCannotAuthorizeAnotherDescriptorOrVirtualDevice() {
        val binding = requireNotNull(evaluate().candidate)
        assertFalse(ButtonSafety.matches(binding, down, ready.copy(device = device.copy(descriptor = "another-device"))))
        assertFalse(ButtonSafety.matches(binding, down, ready.copy(device = device.copy(isVirtual = true))))
        assertFalse(ButtonSafety.matches(binding, down, pending))
    }
    @Test fun reportJsonPreservesExactRequestedFieldsAndLookupStates() {
        val report = evaluate()
        val envelope = CalibrationEnvelope("test", "samsung", "unverified-model", 36, "16", "build", "2026-11-02T00:00:00Z", report)
        val raw = CalibrationReportCodec.encode(envelope)
        assertEquals(envelope, CalibrationReportCodec.decode(raw))
        for (field in listOf("deviceId", "source", "name", "descriptor", "sources", "isVirtual", "PENDING", "READY")) assertTrue(raw.contains(field))
    }
}
