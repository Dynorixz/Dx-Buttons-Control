package com.dynorixz.buttonscontrol.core

data class KeySequenceId(val downTime: Long, val deviceId: Int, val keyCode: Int, val scanCode: Int)
/** The filtering decision cannot change between DOWN and UP, including after settings change.
 * Bounded history also handles an old UP arriving after a new DOWN with a different downTime.
 */
class KeySequenceOwnership(private val capacity: Int = 32) {
    private val decisions = linkedMapOf<KeySequenceId, Boolean>()
    fun tracked(id: KeySequenceId) = id in decisions
    fun begin(id: KeySequenceId, consumed: Boolean) {
        if (id in decisions) return
        if (decisions.size >= capacity) decisions.remove(decisions.keys.first())
        decisions[id] = consumed
    }
    fun decision(id: KeySequenceId): Boolean? = decisions[id]
    fun release(id: KeySequenceId): Boolean? = decisions.remove(id)
}
