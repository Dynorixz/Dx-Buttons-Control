package com.dynorixz.buttonscontrol.core

import org.junit.Assert.*
import org.junit.Test

class ApplicationAvailabilityTest {
    @Test fun publicEnabledActivityAvailable() { assertTrue(ApplicationAvailability.usable(LaunchTarget(true, true, true, true))) }
    @Test fun deletedPackageUnavailable() { assertFalse(ApplicationAvailability.check("deleted.app", false).success) }
    @Test fun noLaunchActivityIsNotInvented() { assertFalse(ApplicationAvailability.check("service.only", false).success) }
    @Test fun disabledAppRejected() { assertFalse(ApplicationAvailability.usable(LaunchTarget(true, false, true, true))) }
    @Test fun disabledActivityRejected() { assertFalse(ApplicationAvailability.usable(LaunchTarget(false, true, true, true))) }
    @Test fun protectedActivityRejected() { assertFalse(ApplicationAvailability.usable(LaunchTarget(true, true, true, false))) }
    @Test fun privateActivityRejected() { assertFalse(ApplicationAvailability.usable(LaunchTarget(true, true, false, true))) }
}
class DiagnosticsRepositoryTest {
    @Test fun bufferIsBounded() { val b = DiagnosticBuffer(3); (1..8).forEach { b.add(DiagnosticEntry(it.toLong(), "key", "code=$it")) }; assertEquals(listOf(6L, 7L, 8L), b.snapshot().map { it.uptimeMs }) }
    @Test fun snapshotDoesNotChangeAfterWrite() { val b = DiagnosticBuffer(); b.add(DiagnosticEntry(1, "action", "ok")); val before = b.snapshot(); b.add(DiagnosticEntry(2, "error", "failed")); assertEquals(1, before.size) }
}
class SettingsCodecTest {
    @Test fun settingsRoundTrip() { val s = DxSettings(hold = GestureSetting(true, Action("app", "installed.app")), favorites = setOf("installed.app")); assertEquals(s, SettingsCodec.decode(SettingsCodec.encode(s))) }
    @Test fun windowsUtf8BomAccepted() { val s = DxSettings(); assertEquals(s, SettingsCodec.decode("\uFEFF" + SettingsCodec.encode(s))) }
    @Test(expected = IllegalArgumentException::class) fun reservedPrimaryStemCannotBeImported() { SettingsCodec.decode(SettingsCodec.encode(DxSettings(button = ButtonBinding(264, 580, "hardware")))) }
    @Test(expected = IllegalArgumentException::class) fun outOfRangeTimeRejected() { SettingsCodec.decode(SettingsCodec.encode(DxSettings(holdMs = 20))) }
    @Test(expected = IllegalArgumentException::class) fun captureModeRequiresBack() { SettingsCodec.decode(SettingsCodec.encode(DxSettings(mode = InputMode.CAPTURE_BACK))) }
    @Test(expected = IllegalArgumentException::class) fun unknownActionRejected() { SettingsCodec.decode(SettingsCodec.encode(DxSettings(hold = GestureSetting(true, Action("fake"))))) }
    @Test(expected = IllegalArgumentException::class) fun tooManyQuickCommandsRejected() { SettingsCodec.decode(SettingsCodec.encode(DxSettings(quickActions = List(7) { Action() }))) }
}
class WatchFaceStateDetectorTest {
    private val hash = "a".repeat(64)
    private val sig = FaceSignature("third.party.face", "FaceActivity", "Frame", listOf("third.party.face:id/face"), hash)
    private val window = WindowSnapshot(sig.packageName, sig.className, sig.rootClass, sig.viewIds, 1, 1, true, hash)
    @Test fun thirdPartyFaceRecognizedFromSignature() { assertTrue(WatchFacePolicy.evaluate(window, sig, true, false, false).onFace) }
    @Test fun samePackageMenuNotFace() { assertFalse(WatchFacePolicy.evaluate(window.copy(className = "AppDrawer"), sig, true, false, false).onFace) }
    @Test fun dynamicGeometryDoesNotDisableFace() { assertTrue(WatchFacePolicy.evaluate(window.copy(topology = "b".repeat(64)), sig, true, false, false).onFace) }
    @Test fun overlayRejected() { assertFalse(WatchFacePolicy.evaluate(window, sig, true, false, true).onFace) }
    @Test fun lockedScreenRejected() { assertFalse(WatchFacePolicy.evaluate(window, sig, true, true, false).onFace) }
    @Test fun aodRejected() { assertFalse(WatchFacePolicy.evaluate(window, sig, false, false, false).onFace) }
    @Test fun unavailableRootRejected() { assertFalse(WatchFacePolicy.evaluate(null, sig, true, false, false).onFace) }
    @Test fun noDistinctiveIdsRejected() { assertFalse(WatchFacePolicy.evaluate(window.copy(viewIds = emptyList()), sig.copy(viewIds = emptyList()), true, false, false).onFace) }
}
