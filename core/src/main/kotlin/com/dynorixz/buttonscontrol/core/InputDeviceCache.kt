package com.dynorixz.buttonscontrol.core

import kotlinx.coroutines.*
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.first

/** Event-driven cache. Device reads never run inside a key callback. */
class InputDeviceCache(
    private val scope: CoroutineScope,
    private val loader: suspend (Int) -> InputDeviceIdentity?,
    private val dispatcher: CoroutineDispatcher = Dispatchers.IO
) {
    private class Entry {
        val state = MutableStateFlow(InputDeviceLookup(DeviceLookupState.PENDING))
        var job: Job? = null
    }
    private val lock = Any()
    private val entries = mutableMapOf<Int, Entry>()
    private var closed = false

    private fun entry(id: Int): Entry {
        val value = synchronized(lock) {
            if (closed) return Entry().apply { state.value = InputDeviceLookup(DeviceLookupState.REMOVED) }
            entries.getOrPut(id) {
                Entry().also { current ->
                    current.job = scope.launch(dispatcher, start = CoroutineStart.LAZY) {
                        val result = try {
                            loader(id)?.let { InputDeviceLookup(DeviceLookupState.READY, it) } ?: InputDeviceLookup(DeviceLookupState.NOT_FOUND)
                        } catch (e: CancellationException) { throw e }
                        catch (e: Exception) { InputDeviceLookup(DeviceLookupState.ERROR, error = e.javaClass.simpleName) }
                        synchronized(lock) { if (entries[id] === current) current.state.value = result }
                    }
                }
            }
        }
        value.job?.start()
        return value
    }

    fun lookup(id: Int): InputDeviceLookup = entry(id).state.value

    suspend fun resolve(id: Int, timeoutMs: Long = 1500): InputDeviceLookup {
        val current = entry(id)
        return withTimeoutOrNull(timeoutMs) { current.state.first { it.state != DeviceLookupState.PENDING } }
            ?: current.state.value
    }

    fun refresh(id: Int) {
        synchronized(lock) { entries.remove(id)?.let { it.state.value = InputDeviceLookup(DeviceLookupState.REMOVED); it.job?.cancel() } }
        entry(id)
    }

    fun removed(id: Int) {
        synchronized(lock) {
            entries.remove(id)?.let { it.state.value = InputDeviceLookup(DeviceLookupState.REMOVED); it.job?.cancel() }
            entries[id] = Entry().apply { state.value = InputDeviceLookup(DeviceLookupState.REMOVED) }
        }
    }

    fun close() {
        synchronized(lock) {
            closed = true
            entries.values.forEach { it.state.value = InputDeviceLookup(DeviceLookupState.REMOVED); it.job?.cancel() }
            entries.clear()
        }
    }
}
