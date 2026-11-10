package com.dynorixz.buttonscontrol.core

import kotlinx.coroutines.*
import kotlinx.coroutines.test.*
import org.junit.Assert.*
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class InputDeviceCacheTest {
    private val device = InputDeviceIdentity(8, "pmic_resin", "physical", 0x101, false)

    @Test fun coldCacheWaitsForMetadataBeforeCalibrationInsteadOfRejectingItAsVirtual() = runTest {
        val dispatcher = StandardTestDispatcher(testScheduler)
        val cache = InputDeviceCache(this, { delay(100); device }, dispatcher)
        assertEquals(DeviceLookupState.PENDING, cache.lookup(8).state)
        val event = ButtonKeyEvent(0, 264, 580, 8, 0x101, 1, 1)
        assertEquals(listOf(ButtonRejection.DEVICE_INFO_PENDING), ButtonSafety.inspect(event, cache.lookup(8)).rejections)
        val result = async { cache.resolve(8) }
        advanceUntilIdle()
        assertTrue(ButtonCalibration.evaluate(event, event.copy(action = 1, eventTime = 90), InputDeviceLookup(DeviceLookupState.PENDING), InputDeviceLookup(DeviceLookupState.PENDING), result.await()).accepted)
        cache.close()
    }
    @Test fun concurrentRequestsUseOneReadAndRetainCachedData() = runTest {
        var reads = 0
        val cache = InputDeviceCache(this, { reads++; delay(10); device }, StandardTestDispatcher(testScheduler))
        repeat(20) { cache.lookup(8) }
        advanceUntilIdle()
        repeat(20) { assertEquals(device, cache.lookup(8).device) }
        assertEquals(1, reads); cache.close()
    }
    @Test fun readFailureHasAnExplicitState() = runTest {
        val cache = InputDeviceCache(this, { throw SecurityException("not logged") }, StandardTestDispatcher(testScheduler))
        val result = async { cache.resolve(8) }; advanceUntilIdle()
        assertEquals(InputDeviceLookup(DeviceLookupState.ERROR, error = "SecurityException"), result.await()); cache.close()
    }
    @Test fun absentInputDeviceIsNotMisclassifiedAsVirtual() = runTest {
        val cache = InputDeviceCache(this, { null }, StandardTestDispatcher(testScheduler))
        val result = async { cache.resolve(8) }; advanceUntilIdle()
        assertEquals(DeviceLookupState.NOT_FOUND, result.await().state); cache.close()
    }
    @Test fun metadataTimeoutStaysUnknownAndNeverOpensTheSafetyGate() = runTest {
        val cache = InputDeviceCache(this, { delay(2000); device }, StandardTestDispatcher(testScheduler))
        val result = async { cache.resolve(8, 100) }
        advanceTimeBy(101); runCurrent()
        assertEquals(DeviceLookupState.PENDING, result.await().state)
        cache.close()
    }
    @Test fun removedDeviceCannotAuthorizePendingCalibration() = runTest {
        val cache = InputDeviceCache(this, { delay(1000); device }, StandardTestDispatcher(testScheduler))
        val result = async { cache.resolve(8) }; runCurrent(); cache.removed(8)
        advanceUntilIdle()
        assertEquals(DeviceLookupState.REMOVED, result.await().state)
        assertNull(cache.lookup(8).device); cache.close()
    }
    @Test fun lateReadCannotOverwriteAReplacementDevice() = runTest {
        var reads = 0
        val cache = InputDeviceCache(this, {
            if (++reads == 1) withContext(NonCancellable) { delay(500); device }
            else device.copy(name = "pmic_pwrkey", descriptor = "replacement")
        }, StandardTestDispatcher(testScheduler))
        cache.lookup(8); runCurrent(); cache.refresh(8); advanceUntilIdle()
        assertEquals("replacement", cache.lookup(8).device?.descriptor)
        assertFalse(ButtonSafety.inspect(ButtonKeyEvent(0, 264, 580, 8, 0x101, 1, 1), cache.lookup(8)).accepted)
        cache.close()
    }
}
