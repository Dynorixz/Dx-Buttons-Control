package com.dynorixz.buttonscontrol.core

import org.junit.Assert.*
import org.junit.Test

class WindowStateTrackerTest {
    private val face = WindowSnapshot("host", "Home", "Frame", listOf("host:id/item_watchface", "host:id/watchface_overlay"), 7, 1, true, trustedHome = true, faceSurfaceVisible = true)
    private val settings = WindowSnapshot("settings", "Settings", "Frame", emptyList(), 9, 2, true)

    @Test fun freshHomeWinsOverLastStateEventFromSettings() {
        val tracker = WindowStateTracker()
        tracker.event(9, "settings", stateChanged = true)
        tracker.publish(tracker.revision, settings, false)
        // Returning Home may emit only WINDOWS_CHANGED, not a new STATE_CHANGED.
        tracker.event(7, null, stateChanged = false, activeChanged = true)
        assertTrue(tracker.publish(tracker.revision, face, false))
        assertEquals(7, tracker.windowId)
        assertEquals("host", tracker.foreground)
        assertTrue(WatchFacePolicy.evaluate(tracker.snapshot, null, true, false, tracker.overlay).onFace)
    }

    @Test fun aStaleMeasurementCannotRestoreFaceAfterAppOpens() {
        val tracker = WindowStateTracker()
        val request = tracker.revision
        tracker.event(9, "settings", stateChanged = true)
        assertFalse(tracker.publish(request, face, false))
        assertNull(tracker.snapshot)
        assertEquals("settings", tracker.foreground)
    }

    @Test fun focusedWindowChangeImmediatelyDisablesCachedFace() {
        val tracker = WindowStateTracker()
        tracker.publish(tracker.revision, face, false)
        tracker.event(9, null, stateChanged = false, activeChanged = true)
        assertNull(tracker.snapshot)
    }

    @Test fun screenOffInvalidatesAnInFlightRead() {
        val tracker = WindowStateTracker()
        val request = tracker.revision
        tracker.invalidate()
        assertFalse(tracker.publish(request, face, false))
        assertNull(tracker.snapshot)
    }

    @Test fun anUnchangedWindowEventDoesNotCancelAnExistingFace() {
        val tracker = WindowStateTracker()
        tracker.publish(tracker.revision, face, false)
        tracker.event(7, "host", stateChanged = true)
        assertEquals(face, tracker.snapshot)
    }

    @Test fun anOverlayFromTheFreshMeasurementBlocksFaceGestures() {
        val tracker = WindowStateTracker()
        tracker.publish(tracker.revision, face, true)
        assertFalse(WatchFacePolicy.evaluate(tracker.snapshot, null, true, false, tracker.overlay).onFace)
    }
}
