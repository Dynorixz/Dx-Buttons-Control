package com.dynorixz.buttonscontrol.core

import kotlinx.coroutines.test.runTest
import org.junit.Assert.*
import org.junit.Test

class ActionExecutorTest {
    private class Platform : ActionPlatform {
        val executed = mutableListOf<String>()
        val unavailable = mutableSetOf<String>()
        var fail: String? = null
        var throwSecurity = false
        override fun availability(action: Action) = ActionResult(action.id !in unavailable, "availability ${action.id}")
        override suspend fun execute(action: Action): ActionResult { if(throwSecurity) throw SecurityException(); executed += action.id; return ActionResult(action.id != fail, "result ${action.id}") }
        override suspend fun pause(milliseconds: Long) {}
    }
    @Test fun unavailableActionNotExecuted() = runTest { val p = Platform(); p.unavailable += "app"; assertFalse(ActionExecutor(p).execute(Action("app", "removed.app")).success); assertTrue(p.executed.isEmpty()) }
    @Test fun allowedActionActuallyInvokesPlatform() = runTest { val p = Platform(); assertTrue(ActionExecutor(p).execute(Action("home")).success); assertEquals(listOf("home"), p.executed) }
    @Test fun sequenceStopsOnFailure() = runTest { val p = Platform(); p.fail = "vibrate"; assertFalse(ActionExecutor(p).execute(Action("sequence", steps = listOf(Action("toast"), Action("vibrate"), Action("home")))).success); assertEquals(listOf("toast", "vibrate"), p.executed) }
    @Test fun sequencePreflightDoesNotPartiallyRun() = runTest { val p = Platform(); p.unavailable += "lock"; assertFalse(ActionExecutor(p).execute(Action("sequence", steps = listOf(Action("toast"), Action("lock")))).success); assertTrue(p.executed.isEmpty()) }
    @Test fun permissionFailureReported() = runTest { val p = Platform(); p.throwSecurity = true; assertFalse(ActionExecutor(p).execute(Action("home")).success) }
    @Test fun unknownActionRejected() = runTest { val p = Platform(); assertFalse(ActionExecutor(p).execute(Action("fake")).success); assertTrue(p.executed.isEmpty()) }
    @Test fun invalidBrightnessRejected() = runTest { val p = Platform(); assertFalse(ActionExecutor(p).execute(Action("brightness", "900")).success); assertTrue(p.executed.isEmpty()) }
    @Test fun nestedSequencePreflightChecksEveryLeaf() = runTest { val p = Platform(); p.unavailable += "lock"; val nested = Action("sequence", steps = listOf(Action("home"), Action("sequence", steps = listOf(Action("lock"))))); assertFalse(ActionExecutor(p).execute(nested).success); assertTrue(p.executed.isEmpty()) }
}
