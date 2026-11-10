package com.dynorixz.buttonscontrol.core

import org.junit.Assert.*
import org.junit.Test

class GestureStateMachineTest {
    private val emitted = mutableListOf<Gesture>()
    private var backs = 0
    private val machine = GestureStateMachine(emitted::add) { backs++ }.apply { updateContext(GestureContext(1, true, true, true)) }
    private fun down(t: Long, repeat: Int = 0, initial: Long = t) = machine.accept(ButtonSample(0, initial, t, repeat))
    private fun up(t: Long, initial: Long) = machine.accept(ButtonSample(1, initial, t))
    private fun tap(t: Long, duration: Long = 60) { down(t); up(t + duration, t) }
    @Test fun singlePassesThrough() { tap(1000); machine.advance(1500); assertTrue(emitted.isEmpty()); assertEquals(0, backs) }
    @Test fun doubleIsDeferred() { tap(1000); tap(1200); assertTrue(emitted.isEmpty()); machine.advance(1691); assertEquals(listOf(Gesture.DOUBLE), emitted) }
    @Test fun tripleNeverEmitsDouble() { tap(1000); tap(1200); tap(1400); machine.advance(2000); assertEquals(listOf(Gesture.TRIPLE), emitted) }
    @Test fun thirdDownCancelsDoubleDeadlineUntilRelease() { tap(1000); tap(1200); down(1500); machine.advance(1700); assertTrue(emitted.isEmpty()); up(1800, 1500); assertEquals(listOf(Gesture.TRIPLE), emitted) }
    @Test fun holdConfirmedByReleaseWithoutRepeats() { machine.configure(GesturePolicy(holdOnTimer = false)); down(1000); machine.advance(1650); assertTrue(emitted.isEmpty()); up(1900, 1000); assertEquals(listOf(Gesture.HOLD), emitted) }
    @Test fun holdConfirmedByRepeatOnce() { down(1000); down(1650, 1, 1000); down(1800, 2, 1000); up(1900, 1000); assertEquals(listOf(Gesture.HOLD), emitted) }
    @Test fun capturedHoldAtThreshold() { machine.configure(GesturePolicy(captured = true)); down(1000); machine.advance(1649); assertTrue(emitted.isEmpty()); machine.advance(1650); up(1800, 1000); assertEquals(listOf(Gesture.HOLD), emitted) }
    @Test fun lostUpDoesNotProduceReleaseOnlyHold() { machine.configure(GesturePolicy(holdOnTimer = false)); down(1000); machine.advance(8000); assertTrue(emitted.isEmpty()); assertFalse(machine.hasPress()) }
    @Test fun observeHoldAtDeadlineWithoutRepeats() { down(1000); assertEquals(1650L, machine.nextDeadline()); machine.advance(1649); assertTrue(emitted.isEmpty()); machine.advance(1650); assertEquals(listOf(Gesture.HOLD), emitted); assertEquals(0, backs) }
    @Test fun timerThenRepeatThenUpExecutesOnce() { down(1000); machine.advance(1650); down(1750, 1, 1000); up(1900, 1000); machine.advance(2500); assertEquals(listOf(Gesture.HOLD), emitted) }
    @Test fun shortUpRemovesHoldDeadline() { down(1000); up(1080, 1000); machine.advance(1650); assertTrue(emitted.isEmpty()) }
    @Test fun screenOffBeforeDeadlineCancelsHold() { down(1000); machine.updateContext(GestureContext(1, false, false, false)); machine.advance(1650); assertTrue(emitted.isEmpty()) }
    @Test fun faceMetadataLossDoesNotCancelGlobalHold() { down(1000); machine.updateContext(GestureContext(1, false, true, true)); machine.advance(1650); assertEquals(listOf(Gesture.HOLD), emitted) }
    @Test fun faceMetadataChangeCancelsPendingDouble() { tap(1000); tap(1200); machine.updateContext(GestureContext(1, false, true, true)); machine.updateContext(GestureContext(1, true, true, true)); machine.advance(2000); assertTrue(emitted.isEmpty()) }
    @Test fun cancelBeforeHoldDeadlineDoesNotExecute() { down(1000); machine.cancel(); machine.advance(1650); assertTrue(emitted.isEmpty()) }
    @Test fun lateTimerBeyondMissingUpTimeoutDoesNotExecute() { down(1000); machine.advance(8000); assertTrue(emitted.isEmpty()) }
    @Test fun orphanUpIsIgnored() { up(1100, 1000); assertTrue(emitted.isEmpty()) }
    @Test fun repeatWithoutDownIgnored() { down(1900, 1, 1000); up(2000, 1000); assertTrue(emitted.isEmpty()) }
    @Test fun mismatchedUpCancels() { down(1000); up(1800, 1100); assertTrue(emitted.isEmpty()) }
    @Test fun newDownWithoutUpCancels() { down(1000); down(1200); up(1300, 1200); machine.advance(2000); assertTrue(emitted.isEmpty()) }
    @Test fun duplicateDownWithZeroRepeatDoesNotCountAsTap() { down(1000); down(1001, 0, 1000); up(1060, 1000); machine.advance(1500); assertTrue(emitted.isEmpty()) }
    @Test fun canceledSequenceNeverExecutes() { tap(1000); down(1200); machine.accept(ButtonSample(1, 1200, 1260, canceled = true)); machine.advance(2000); assertTrue(emitted.isEmpty()) }
    @Test fun outOfOrderCancels() { down(1000); down(999, 1, 1000); up(1800, 1000); assertTrue(emitted.isEmpty()) }
    @Test fun rapidBounceRejected() { tap(1000, 5); tap(1020, 5); tap(1040, 5); machine.advance(2000); assertTrue(emitted.isEmpty()) }
    @Test fun slowTapsNotDouble() { tap(1000); tap(1600); machine.advance(2200); assertTrue(emitted.isEmpty()) }
    @Test fun fourClicksEmitOneTripleOnly() { tap(1000); tap(1200); tap(1400); tap(1600); machine.advance(2500); assertEquals(listOf(Gesture.TRIPLE), emitted) }
    @Test fun sixClicksInOneBurstEmitOneTriple() { (0..5).forEach { tap(1000 + it * 140L) }; machine.advance(3000); assertEquals(listOf(Gesture.TRIPLE), emitted) }
    @Test fun subsequentSeparateBurstWorks() { tap(1000); tap(1200); tap(1400); tap(2400); tap(2600); machine.advance(3100); assertEquals(listOf(Gesture.TRIPLE, Gesture.DOUBLE), emitted) }
    @Test fun cancelBeforeTimeoutDoesNotEmit() { tap(1000); tap(1200); machine.cancel(); machine.advance(2000); assertTrue(emitted.isEmpty()) }
    @Test fun holdResetsPendingDouble() { tap(1000); tap(1200); down(1400); up(2100, 1400); machine.advance(2600); assertEquals(listOf(Gesture.HOLD), emitted) }
    @Test fun outsideFaceRejectsClicksAllowsHold() { machine.updateContext(GestureContext(2, false, true, true)); tap(1000); tap(1200); machine.advance(1800); down(2000); up(2800, 2000); assertEquals(listOf(Gesture.HOLD), emitted) }
    @Test fun exclusionDisablesHold() { machine.updateContext(GestureContext(2, false, false, true)); down(1000); up(2000, 1000); assertTrue(emitted.isEmpty()) }
    @Test fun windowChangeCancelsDouble() { tap(1000); tap(1200); machine.updateContext(GestureContext(2, true, true, true)); machine.advance(2000); assertTrue(emitted.isEmpty()) }
    @Test fun windowChangeCancelsHold() { down(1000); machine.updateContext(GestureContext(2, false, true, true)); up(1900, 1000); assertTrue(emitted.isEmpty()) }
    @Test fun screenOffCancels() { tap(1000); tap(1200); machine.updateContext(GestureContext(2, true, true, false)); machine.advance(2000); assertTrue(emitted.isEmpty()) }
    @Test fun unassignedTripleSuppressesDouble() { machine.configure(GesturePolicy(triple = false)); tap(1000); tap(1200); tap(1400); machine.advance(2000); assertTrue(emitted.isEmpty()) }
    @Test fun allDisabledPassesThrough() { machine.configure(GesturePolicy(hold = false, double = false, triple = false)); tap(1000); tap(1200); down(2000); up(2800, 2000); assertTrue(emitted.isEmpty()) }
    @Test fun capturedSingleReplaysBack() { machine.configure(GesturePolicy(captured = true)); tap(1000); machine.advance(1500); assertEquals(1, backs); assertTrue(emitted.isEmpty()) }
    @Test fun exactGapStillTriple() { tap(1000); tap(1200); down(1610); up(1660, 1610); assertEquals(listOf(Gesture.TRIPLE), emitted) }
    @Test fun stableContextDoesNotCancel() { tap(1000); machine.updateContext(GestureContext(1, true, true, true)); tap(1200); machine.advance(1800); assertEquals(listOf(Gesture.DOUBLE), emitted) }
    @Test fun unassignedCapturedHoldReplaysBack() { machine.configure(GesturePolicy(captured = true, hold = false)); down(1000); up(1900, 1000); assertEquals(1, backs); assertTrue(emitted.isEmpty()) }
    @Test fun unassignedCapturedTripleReplaysBackWithoutDouble() { machine.configure(GesturePolicy(captured = true, triple = false)); tap(1000); tap(1200); tap(1400); assertEquals(1, backs); assertTrue(emitted.isEmpty()) }
    @Test fun queuedThirdDownWinsOverDoubleTimer() {
        tap(1000); tap(1200); machine.advance(1640); assertTrue(emitted.isEmpty())
        down(1600); up(1660,1600); machine.advance(2100)
        assertEquals(listOf(Gesture.TRIPLE),emitted)
    }
    @Test fun pressOutsideLogicalGapDoesNotMergeDuringDeliveryGrace() {
        tap(1000); tap(1200); down(1620); up(1680,1620); machine.advance(2200)
        assertEquals(listOf(Gesture.DOUBLE),emitted)
    }
    @Test fun fastHumanTripleNotBounce() {
        tap(1000,40); tap(1080,40); tap(1160,40); machine.advance(2000)
        assertEquals(listOf(Gesture.TRIPLE),emitted)
    }
    @Test fun doubleWaitsForDeliveryGrace() {
        tap(1000); tap(1200); assertEquals(1691L,machine.nextDeadline())
        machine.advance(1690); assertTrue(emitted.isEmpty()); machine.advance(1691)
        assertEquals(listOf(Gesture.DOUBLE),emitted)
    }
}
