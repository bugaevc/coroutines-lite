package io.github.bugaevc.coroutineslite

import kotlin.coroutines.Continuation
import kotlin.coroutines.CoroutineContext
import kotlin.coroutines.EmptyCoroutineContext
import kotlin.coroutines.cancellation.CancellationException
import kotlin.coroutines.startCoroutine

/**
 * A coroutine scope is the primary user-facing concurrency API.
 *
 * Internally, a coroutine scope wraps a [coroutineContext] that has a [Job],
 * and serves as a factory for creating child jobs.
 *
 * A coroutine scope can be created and cancelled directly, like this:
 *
 * ```
 * // Create a scope:
 * val scope = CoroutineScope(Dispatchers.Default + myExceptionHandler)
 * // We can launch some asynchronous work now:
 * scope.launch {
 *     someWork()
 * }
 * scope.launch {
 *     someOtherWork()
 * }
 * // Later, we can cancel the scope:
 * scope.cancel()
 * ```
 *
 * It's typically a good idea to create a scope for objects that have a
 * lifecycle, and clean up the scope when the object is destroyed.
 *
 * In asynchronous context, the preferred way to create a scope is
 * the [coroutineScope] builder function:
 *
 * ```
 * coroutineScope {
 *     launch {
 *         someWork()
 *     }
 *     launch {
 *         someOtherWork()
 *     }
 * }
 * ```
 */
open class CoroutineScope(context: CoroutineContext) {
    /**
     * The context of this scope.
     *
     * All the jobs spawned in this scope with [launch] and [async] will
     * inherit this context.
     *
     * The context always contains a [Job] (which represents this whole scope).
     * Jobs spawned in this scope will become child jobs of this job.
     */
    // Ensure context has a job, which will represent this scope.
    val coroutineContext: CoroutineContext = when {
        // If the context we got already has a job, great, we'll just use that.
        // In this case we assume that there may be some code running in this
        // job, and someone else (our creator) will figure out completing the
        // job and propagating any errors, so we don't set up our own error
        // handling.
        context[Job] != null -> context
        // If we did not get a job, create a new one now with no parent. In
        // this case, we consider ourselves responsible for propagating any
        // errors in the job. We assume no code will run in this new job
        // directly (only in its child jobs), and so this cancellation handler
        // we're about to establish won't conflict with the ones the code could
        // establish when it suspends.
        else -> {
            val job = Job(parent = null)
            job.invokeOnCancellation { throwable ->
                handleException(throwable)
            }
            context + job
        }
    }

    /**
     * Find the job in our context.
     *
     * We have ensured the context has a job in it, so get it.
     */
    private fun findJob(): Job = coroutineContext[Job]!!

    /**
     * Our top-level job was cancelled with an exception.
     *
     * Try to dispatch the exception to a handler.
     */
    private fun handleException(throwable: Throwable) {
        // Do nothing if it was regular cancellation.
        if (throwable is CancellationException) {
            return
        }
        val handler = coroutineContext[CoroutineExceptionHandler]
        if (handler != null) {
            // If our context has a CoroutineExceptionHandler, use that.
            handler.handleException(coroutineContext, throwable)
        } else {
            // Otherwise, send it to Thread.uncaughtExceptionHandler.
            val thread = Thread.currentThread()
            val uncaughtHandler = thread.uncaughtExceptionHandler
            uncaughtHandler.uncaughtException(thread, throwable)
        }
    }

    val isActive: Boolean
        get() = findJob().isActive

    /**
     * Ensure this scope is active; otherwise throw an exception.
     *
     * There's no need to call this function if you suspend with
     * [suspendCancellableCoroutine], because it automatically handles
     * cancellation. However, if you suspend non-cancellably, or block
     * the thread in some other way, calling this function is a good way
     * to ensure the work terminates promptly after it's cancelled.
     */
    fun ensureActive() {
        findJob().ensureActive()
    }

    /**
     * Cancel this scope and all the jobs running in it.
     */
    fun cancel(cause: Throwable = CancellationException()) {
        findJob().cancel(cause)
    }

    /**
     * Create a new coroutine context suitable for starting a job in this scope
     * by combining the [context of this scope][coroutineContext] with the
     * provided [context].
     */
    fun newCoroutineContext(context: CoroutineContext): CoroutineContext {
        // If neither context has a dispatcher, add the default one.
        return Dispatchers.Default + this.coroutineContext + context
    }

    /**
     * Launch a new asynchronous job in this scope, running [block].
     *
     * This method is intended for fire-and-forget launching of jobs. If you
     * need to execute a block of code asynchronously and later retrieve its
     * result, use [async] instead.
     *
     * You can optionally specify a [context] for the new job to run in.
     * For instance, you can write:
     *
     * ```
     * launch(Dispatchers.IO) {
     *     // ...
     * }
     * ```
     *
     * instead of the longer:
     *
     * ```
     * launch {
     *     withContext(Dispatchers.IO) {
     *         // ...
     *     }
     * }
     * ```
     *
     * @see async
     */
    fun launch(
        context: CoroutineContext = EmptyCoroutineContext,
        block: suspend CoroutineScope.() -> Unit,
    ): Job {
        // Create context for the child job, and the child job itself. The job
        // will register itself as our child job automatically.
        val newContext = newCoroutineContext(context)
        val job = Job(parent = newContext[Job])
        val combinedContext = newContext + job
        // When the block completes, have it call job.completeWith().
        val continuation = Continuation<Unit>(
            context = combinedContext,
            resumeWith = job::completeWith,
        )
        block.startCoroutine(receiver = this, completion = continuation)
        return job
    }

    /**
     * Launch [block] in this scope, and get a handle to its eventual result.
     *
     * This method is intended for running logic that has a meaningful output
     * result, which can be retrieved by calling [Deferred.await] on the return
     * value of this method.
     *
     * You can optionally specify a [context] for the new job to run in.
     * For instance, you can write:
     *
     * ```
     * async(Dispatchers.IO) {
     *     // ...
     * }
     * ```
     *
     * instead of the longer:
     *
     * ```
     * async {
     *     withContext(Dispatchers.IO) {
     *         // ...
     *     }
     * }
     * ```
     *
     * @see launch
     */
    fun <R> async(
        context: CoroutineContext = EmptyCoroutineContext,
        block: suspend CoroutineScope.() -> R,
    ): Deferred<R> {
        // Same as above, but use a Deferred instead of a plain Job.
        val newContext = newCoroutineContext(context)
        val deferred = DeferredImpl<R>(parent = newContext[Job])
        val combinedContext = newContext + deferred
        val continuation = Continuation(combinedContext, deferred::complete)
        block.startCoroutine(receiver = this, completion = continuation)
        return deferred
    }
}