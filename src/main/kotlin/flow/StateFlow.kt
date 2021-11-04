package io.github.bugaevc.coroutineslite.flow

import io.github.bugaevc.coroutineslite.suspendCancellableCoroutine
import kotlin.coroutines.Continuation
import kotlin.coroutines.resume

/**
 * State flow stores a value and emits when the value is updated.
 *
 * You can additionally always get the current [value] directly, without
 * suspending. The actual implementation of the state flow interface,
 * [MutableStateFlow], also lets you modify the value.
 *
 * Multiple collectors can collect the same state flow concurrently. They will
 * all see an emission of the new value when the value is modified.
 *
 * A state flow always behaves as if [distinctUntilChanged] and [conflate]
 * modifiers had been applied; it will never emit several equal values is a
 * row, and will always emit the latest value.
 */
sealed interface StateFlow<out T> : Flow<T> {
    val value: T
}

/**
 * Mutable variant, and the underlying implementation of, [StateFlow].
 *
 * The [value] may be read and written to at any time, without suspending.
 * It's also possible to atomically update the value using [compareAndSet].
 */
class MutableStateFlow<T>(initialValue: T) : StateFlow<T> {
    // Collectors suspended inside collect().
    private val collectors = mutableListOf<Continuation<Boolean>>()

    override var value: T = initialValue
        set(v) {
            // Assign the new value and wake all the collectors.
            val c: Array<Continuation<Boolean>>
            synchronized(this) {
                field = v
                c = collectors.toTypedArray()
                collectors.clear()
            }
            for (collector in c) {
                collector.resume(false)
            }
        }

    /**
     * Atomically update the value from [expect] to [update].
     *
     * @return whether the atomic update succeeded.
     */
    fun compareAndSet(expect: T, update: T): Boolean {
        val currentValue = value
        // Perform a full equals() call while not holding the monitor.
        if (currentValue != expect) {
            return false
        }
        val c: Array<Continuation<Boolean>>
        synchronized(this) {
            // Do a shallow identity check: if it doesn't match, that
            // means it has been changed since our full check above.
            // Fail (spuriously if the value is actually equal).
            if (value !== currentValue) {
                return false
            }
            value = update
            c = collectors.toTypedArray()
            collectors.clear()
        }
        for (collector in c) {
            collector.resume(false)
        }
        return true
    }

    override suspend fun collect(collector: FlowCollector<T>) {
        // Start by emitting the current value.
        var lastEmitted = value
        collector.emit(lastEmitted)
        while (true) {
            var toEmit: T? = null
            // Suspend now; we'll resume if we find a value
            // to emit without actually suspending.
            val found = suspendCancellableCoroutine<Boolean> { cont ->
                // Loop while we see equal values; typically we should
                // only loop here once or twice.
                while (true) {
                    val candidate: T = synchronized(this) {
                        // If the current value is the exact same one we
                        // have emitted the last time, then do actually
                        // suspend, and wait for someone to wake us up.
                        if (value === lastEmitted) {
                            collectors.add(cont)
                            cont.invokeOnCancellation {
                                synchronized(this) {
                                    collectors.remove(cont)
                                }
                            }
                            return@suspendCancellableCoroutine
                        }
                        // Otherwise, we should consider emitting this
                        // current value.
                        value
                    }
                    // Compare the candidate value against the one we
                    // emitted last with the monitor released.
                    if (candidate != lastEmitted) {
                        // It is actually different; so we're going to emit
                        // it. Remember the value and resume ourselves.
                        toEmit = candidate
                        cont.resume(true)
                        return@suspendCancellableCoroutine
                    }
                    // It's equal to the last value; now loop again.
                    // If the value has not been changed yet again while
                    // we didn't hold the monitor, on the next iteration
                    // we'll recognize the current value as the same one
                    // as this one, and will suspend.
                    lastEmitted = candidate
                }
                // Should never get here.
            }
            // We got resumed, that means there's a new value for us to emit.
            // If 'found' is true (i.e. when we have resumed ourselves), we
            // have already found and identified the value, so all that's left
            // to do is for us to actually emit it. If `found` is false (i.e.
            // we have been resumed by someone changing the value), we should
            // loop once more to see if we actually want to emit this new value
            // or not.
            if (found) {
                // We use this instead of the usual toEmit!!, because T can be
                // nullable.
                @Suppress("UNCHECKED_CAST")
                lastEmitted = toEmit as T
                collector.emit(lastEmitted)
            }
        }
    }
}