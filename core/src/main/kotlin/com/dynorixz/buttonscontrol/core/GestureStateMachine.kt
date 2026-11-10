package com.dynorixz.buttonscontrol.core

data class ButtonSample(val action: Int, val downTime: Long, val eventTime: Long, val repeat: Int = 0, val canceled: Boolean = false)
data class GestureContext(val epoch: Long, val onFace: Boolean, val holdAllowed: Boolean, val interactive: Boolean)
data class GesturePolicy(val holdMs: Long = 650, val gapMs: Long = 350, val debounceMs: Long = 25, val hold: Boolean = true, val double: Boolean = true, val triple: Boolean = true, val captured: Boolean = false, val holdOnTimer: Boolean = true, val deliveryGraceMs: Long = 80)

/** All time values are uptime milliseconds. No threads, clocks or Android objects are retained.
 * A one-shot deadline enables holds on hardware that sends no repeats, while key events
 * can pass through unchanged. Release-only mode provides protection against lost UP.
 */
class GestureStateMachine(private val emit: (Gesture) -> Unit, private val singleBack: () -> Unit = {}) {
    var policy = GesturePolicy()
        private set
    private var context = GestureContext(0, false, false, false)
    private var down: ButtonSample? = null
    private var lastTime = -1L
    private var taps = 0
    private var lastUp = -1L
    private var held = false
    private var terminal = false
    private var invalidPress = false
    private val missingUpMs = 6000L

    fun configure(value: GesturePolicy) { cancel(); policy = value }
    fun updateContext(value: GestureContext) {
        if (value.epoch != context.epoch || value.interactive != context.interactive || value.holdAllowed != context.holdAllowed) cancel()
        else if (value.onFace != context.onFace) {
            // Face metadata can disappear while the physical button remains down.
            taps = 0; lastUp = -1; terminal = down != null
        }
        context = value
    }
    fun cancel() { down = null; taps = 0; held = false; terminal = false; invalidPress = false; lastUp = -1; lastTime = -1 }
    fun hasPress() = down != null

    fun accept(e: ButtonSample) {
        if (e.action !in 0..1) return
        if (!context.interactive || e.canceled || e.eventTime < e.downTime || e.eventTime < lastTime) { cancel(); return }
        // Do not emit a pending double before validating an orphan UP or duplicate DOWN.
        if (e.action == 1 && (down == null || down?.downTime != e.downTime)) { cancel(); return }
        if (e.action == 0 && down != null && down?.downTime != e.downTime) { cancel(); invalidPress = true; lastTime = e.eventTime; return }
        if (e.action == 0 && down == null && e.repeat > 0) { cancel(); return }
        // Classify a new press by event time, not the time its callback reached the service.
        // A short delivery grace prevents a queued third DOWN racing the double timer.
        if (e.action == 0 && down == null && lastUp >= 0 && e.eventTime > lastUp + policy.gapMs) finishBurst()
        advance(e.eventTime)
        lastTime = e.eventTime
        if (e.action == 0) {
            if (down != null) {
                if (e.repeat > 0 && e.eventTime - e.downTime >= policy.holdMs) confirmHold()
                return
            }
            if (invalidPress) return
            down = e
            held = false
        } else {
            val press = down ?: return
            down = null
            if (held) { taps = 0; lastUp = e.eventTime; terminal = true; return }
            val duration = e.eventTime - press.downTime
            if (duration >= policy.holdMs) {
                confirmHold()
                if (policy.captured && !(policy.hold && context.holdAllowed)) singleBack()
                taps = 0; terminal = true; lastUp = e.eventTime
                return
            }
            if (duration < policy.debounceMs || (lastUp >= 0 && press.eventTime - lastUp < policy.debounceMs)) {
                taps = 0; terminal = true; lastUp = e.eventTime; return
            }
            if (terminal) { lastUp = e.eventTime; return }
            if (!context.onFace || !(policy.double || policy.triple)) {
                if (policy.captured) singleBack()
                lastUp = e.eventTime; return
            }
            taps++
            lastUp = e.eventTime
            if (taps == 3) {
                // A triple suppresses double even when no triple action is assigned.
                if (policy.triple) emit(Gesture.TRIPLE)
                else if (policy.captured) singleBack()
                taps = 0; terminal = true
            }
        }
    }

    fun advance(now: Long) {
        down?.let {
            if (now - it.downTime >= missingUpMs) { cancel(); return }
            if (policy.holdOnTimer && now - it.downTime >= policy.holdMs) confirmHold()
            return
        }
        if (lastUp >= 0 && now > lastUp + policy.gapMs + policy.deliveryGraceMs) finishBurst()
    }
    fun nextDeadline(): Long? = down?.let {
        if (policy.holdOnTimer && !held && policy.hold && context.holdAllowed) it.downTime + policy.holdMs
        else it.downTime + missingUpMs
    } ?: lastUp.takeIf { it >= 0 }?.plus(policy.gapMs + policy.deliveryGraceMs + 1)

    private fun finishBurst() {
        val count = taps
        taps = 0; lastUp = -1; terminal = false
        if (count == 2 && context.onFace && policy.double) emit(Gesture.DOUBLE)
        else if (count == 1 && policy.captured) singleBack()
    }

    private fun confirmHold() {
        taps = 0; terminal = true
        if (!held && policy.hold && context.holdAllowed) { held = true; emit(Gesture.HOLD) }
    }
}
