package io.github.bugaevc.coroutineslite

import kotlin.coroutines.Continuation
import kotlin.coroutines.ContinuationInterceptor
import kotlin.coroutines.coroutineContext

/**
 * Delay is an interface [ContinuationInterceptor]s can implement to support
 * for delaying execution of continuations.
 *
 * In practice, [EventLoop]-derived [CoroutineDispatcher]s implement Delay.
 */
interface Delay {
    /**
     * Schedule to resume [continuation] after a delay of [timeMillis].
     *
     * Note: there's no way to cancel a scheduled continuation, so the
     * continuation should return quickly if it determines it has been
     * cancelled. This is handled by [CancellableContinuation].
     */
    fun scheduleResumeAfterDelay(
        timeMillis: Long,
        continuation: Continuation<Unit>,
    )
}

/**
 * Delay the current coroutine on the specific instance of [Delay].
 */
suspend fun Delay.delay(timeMillis: Long) {
    suspendCancellableCoroutine<Unit> { cont ->
        scheduleResumeAfterDelay(timeMillis, cont)
    }
}

/**
 * Delay the current coroutine for [timeMillis].
 */
suspend fun delay(timeMillis: Long) {
    // Pick an instance of Delay we're going to use to delay ourselves.
    // If the current continuation interceptor implements delay, use that.
    // Otherwise, just use the default dispatcher.
    val interceptor = coroutineContext[ContinuationInterceptor]
    val delay = interceptor as? Delay ?: Dispatchers.Default as Delay
    delay.delay(timeMillis)
}