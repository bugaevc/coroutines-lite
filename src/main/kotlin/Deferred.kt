package io.github.bugaevc.coroutineslite

/**
 * A job that will eventually produce a value.
 *
 * Call [await] to get the value, suspending until the value is available.
 *
 * Instances of `Deferred` are created by calling [CoroutineScope.async].
 */
sealed class Deferred<out R>(parent: Job? = null) : Job(parent) {
    /**
     * Wait until this job completes, and get the value it produces.
     *
     * If the job completes exceptionally, re-throw the exception.
     *
     * `await()` may be called multiple times on the same `Deferred` instance;
     * each caller will either get a reference to the same produced value, or
     * throw the same exception.
     *
     * @see join
     */
    abstract suspend fun await(): R
}

internal class DeferredImpl<R>(parent: Job? = null) : Deferred<R>(parent) {
    private var result: Result<R>? = null

    internal fun complete(result: Result<R>) {
        synchronized(this) {
            this.result = result
        }
        completeWith(result.map { })
    }

    override suspend fun await(): R {
        join()
        val cause = cancellationCause
        if (cause != null) {
            throw cause
        }
        return result!!.getOrThrow()
    }
}

/**
 * Await multiple deferreds concurrently.
 */
suspend fun <T> Collection<Deferred<T>>.awaitAll(): List<T> {
    return map { deferred ->
        deferred.await()
    }
}