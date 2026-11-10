package com.dynorixz.buttonscontrol.core

import org.junit.Assert.*
import org.junit.Test

class KeySequenceOwnershipTest {
    private val id = KeySequenceId(1000, 1, 4, 158)
    @Test fun downUpDecisionRemainsConsistent() { val owner = KeySequenceOwnership(); owner.begin(id, true); assertEquals(true, owner.decision(id)); assertEquals(true, owner.release(id)); assertNull(owner.decision(id)) }
    @Test fun repeatedDownCannotChangeDecision() { val owner = KeySequenceOwnership(); owner.begin(id, true); owner.begin(id, false); assertEquals(true, owner.release(id)) }
    @Test fun observeDecisionRemainsFalse() { val owner = KeySequenceOwnership(); owner.begin(id, false); owner.begin(id, true); assertEquals(false, owner.release(id)) }
    @Test fun oldUpAfterMissingUpNewDownStillConsumed() { val owner = KeySequenceOwnership(); owner.begin(id, true); owner.begin(id.copy(downTime = 2000), false); assertEquals(true, owner.release(id)); assertEquals(false, owner.release(id.copy(downTime = 2000))) }
    @Test fun otherDeviceCannotConsumeUp() { val owner = KeySequenceOwnership(); owner.begin(id, true); assertNull(owner.release(id.copy(deviceId = 2))); assertEquals(true, owner.release(id)) }
    @Test fun missingUpHistoryIsBounded() { val owner = KeySequenceOwnership(2); owner.begin(id, true); owner.begin(id.copy(downTime = 2000), true); owner.begin(id.copy(downTime = 3000), true); assertNull(owner.decision(id)); assertEquals(true, owner.release(id.copy(downTime = 3000))) }
}
