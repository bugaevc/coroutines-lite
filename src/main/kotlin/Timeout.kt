package io.github.bugaevc.coroutineslite

import kotlin.coroutines.cancellation.CancellationException

/**
 * A special kind of cancellation exception that is specifically raised
 * to indicate that the result is no longer needed because a timeout has
 * expired.
 */
class TimeoutCancellationException : CancellationException()

/**
 * Run [block]; if it doesn't complete in [timeMillis], cancel it and throw
 * [TimeoutCancellationException].
 *
 * @see withTimeoutOrNull
 */
suspend fun <T> withTimeout(
    timeMillis: Long,
    block: suspend CoroutineScope.() -> T,
): T {
    // Run the block and a delay() call concurrently.
    return coroutineScope {
        val timeoutJob = launch {
            delay(timeMillis)
            // If the delay returns first, cancel this whole scope.
            // This will throw the exception from the withTimeout() call.
            cancel(TimeoutCancellationException())
        }
        val result = block()
        // If the block returns first, cancel the delay.
        timeoutJob.cancel()
        return@coroutineScope result
    }
}

/**
 * Run [block]; if it doesn't complete in [timeMillis], cancel it and return
 * null.
 *
 * @see withTimeout
 */
suspend fun <T> withTimeoutOrNull(
    timeMillis: Long,
    block: suspend CoroutineScope.() -> T,
): T? {
    // Run the block and a delay() call concurrently.
    var ourException: TimeoutCancellationException? = null
    return try {
        coroutineScope {
            val timeoutJob = launch {
                delay(timeMillis)
                // If the delay returns first, cancel this whole scope.
                // Keep track of the specific exception we raise.
                val ex = TimeoutCancellationException()
                ourException = ex
                cancel(ex)
            }
            val result = block()
            // If the block returns first, cancel the delay.
            timeoutJob.cancel()
            return@coroutineScope result
        }
    } catch (ex: TimeoutCancellationException) {
        // Alright, we have caught a TimeoutCancellationException.
        // If it's the same one that we have thrown, then it happened
        // because of *our* timeout expiring, so we return null. Otherwise,
        // it might be someone else's timeout expiring (such as another
        // withTimeout()/withTimeoutOrNull() call up the call stack); in
        // that case we don't want to eat the exception; so rethrow it.
        if (ex !== ourException) {
            throw ex
        }
        null
    }
}