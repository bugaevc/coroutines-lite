package io.github.bugaevc.coroutineslite.flow

import io.github.bugaevc.coroutineslite.Job
import io.github.bugaevc.coroutineslite.delay

/**
 * Skip values that are followed by another value in less then [timeoutMillis].
 *
 * This will delay each value by [timeoutMillis], and skip the value altogether
 * if another value is emitted during that time. If the upstream flow
 * completes, the last value it has emitted gets re-emitted immediately.
 *
 * Note that the resulting flow will not emit anything at all if the upstream
 * flow keeps emitting values faster than [timeoutMillis].
 *
 * Consider using [distinctUntilChanged] before `debounce` in order to prevent
 * consecutive equal elements from cancelling each other.
 */
fun <T> Flow<T>.debounce(timeoutMillis: Long): Flow<T> = channelFlow {
    var emitJob: Job? = null
    var delayJob: Job? = null
    collect { value ->
        emitJob?.cancelAndJoin()
        delayJob?.cancelAndJoin()
        // Wait for the configured timeout; if a new value gets emitted
        // while we're waiting, or the upstream flow completes, we'll get
        // cancelled.
        val newDelayJob = launch {
            delay(timeoutMillis)
        }
        delayJob = newDelayJob
        emitJob = launch {
            // Wait for the delay job. Using newDelayJob instead of delayJob!!
            // makes sure we wait for *our* delay job and not the latest one
            // at the moment this block happens to execute. The delay job will
            // get cancelled if a new value is emitted (in that case, we'll be
            // cancelled along with it), or if the upstream flow completes (in
            // which case we go ahead and emit the new value immediately).
            newDelayJob.join()
            // No new value has appeared, send ours to the collector.
            send(value)
        }
    }
    // The upstream flow is complete; we know for sure it won't emit more
    // values. If we're delaying the last value, stop doing that now, and
    // emit the last value immediately.
    delayJob?.cancel()
}