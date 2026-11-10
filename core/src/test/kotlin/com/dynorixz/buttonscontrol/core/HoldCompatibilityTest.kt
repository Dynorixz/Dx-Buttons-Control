package com.dynorixz.buttonscontrol.core

import org.junit.Assert.*
import org.junit.Test

class HoldCompatibilityTest {
    @Test fun samsungBackSourceAccepted() { assertTrue(ButtonSafety.allowed(ButtonBinding(264, 580, "physical", "pmic_resin"))) }
    @Test fun samsungAlternateScanAccepted() { assertTrue(ButtonSafety.allowed(ButtonBinding(264, 114, "physical", "pmic_resin"))) }
    @Test fun primaryStemOnPowerDriverRejected() { assertFalse(ButtonSafety.allowed(ButtonBinding(264, 580, "physical", "pmic_pwrkey"))) }
    @Test fun powerKeyOnBackDriverStillRejected() { assertFalse(ButtonSafety.allowed(ButtonBinding(26, 580, "physical", "pmic_resin"))) }
    @Test fun unknownPrimarySourceRejected() { assertFalse(ButtonSafety.allowed(ButtonBinding(264, 580, "physical"))) }
    @Test fun unverifiedScanRejected() { assertFalse(ButtonSafety.allowed(ButtonBinding(264, 116, "physical", "pmic_resin"))) }
    @Test fun emptyDescriptorRejected() { assertFalse(ButtonSafety.allowed(ButtonBinding(264, 580, "", "pmic_resin"))) }
    @Test fun hardwareBindingSurvivesSettingsRoundTrip() {
        val s = DxSettings(button = ButtonBinding(264, 580, "physical", "pmic_resin"))
        assertEquals(s, SettingsCodec.decode(SettingsCodec.encode(s)))
    }
    @Test(expected = IllegalArgumentException::class) fun primarySourceCannotCapture() {
        SettingsCodec.decode(SettingsCodec.encode(DxSettings(button = ButtonBinding(264, 580, "physical", "pmic_resin"), mode = InputMode.CAPTURE_BACK)))
    }
    @Test fun unknownWatchFaceRootDoesNotBlockHoldWithoutExclusions() { assertTrue(HoldAvailability.allowed(true, null, emptySet())) }
    @Test fun unknownPackageWithExclusionsFailsClosed() { assertFalse(HoldAvailability.allowed(true, null, setOf("excluded.app"))) }
    @Test fun excludedPackageBlocksHold() { assertFalse(HoldAvailability.allowed(true, "excluded.app", setOf("excluded.app"))) }
    @Test fun knownAllowedPackageSupportsHold() { assertTrue(HoldAvailability.allowed(true, "face.app", setOf("excluded.app"))) }
    @Test fun sleepingScreenBlocksHold() { assertFalse(HoldAvailability.allowed(false, "face.app", emptySet())) }
}
