package io.github.bugaevc.coroutineslite

import kotlin.coroutines.*
import kotlin.coroutines.cancellation.CancellationException
import kotlin.coroutines.intrinsics.COROUTINE_SUSPENDED
import kotlin.coroutines.intrinsics.intercepted
import kotlin.coroutines.intrinsics.suspendCoroutineUninterceptedOrReturn

fun interface CancellationHandler {
    fun handleCancellation(cause: Throwable)
}

/**
 * Wrapper for a continuation that adds cancellation support.
 *
 * CancellableContinuation will register to get cancelled when the current
 * [Job] gets cancelled. Alternatively, it may be cancelled explicitly by
 * calling [cancel]. In either case, the wrapped continuation will be resumed
 * with an exception (typically, [CancellationException]).
 *
 * While it's only valid to call [resumeWith] once, [cancel] may be called
 * multiple times. The [cancel] calls may happen concurrently with each other
 * and with a call to [resumeWith]. CancellableContinuation implements a state
 * machine that tracks whether it has been resumed or cancelled, and only
 * resumes the wrapped continuation once.
 */
class CancellableContinuation<in T>(
    private val wrappedContinuation: Continuation<T>,
) : Continuation<T> {

    private var resumed = false

    // If we have been cancelled, this is non-null and stores the cause.
    private var cancellationCause: Throwable? = null

    // A handler to invoke when cancelled, if any.
    private var cancellationHandler: CancellationHandler? = null

    /**
     * Whether this continuation has not yet been cancelled or resumed.
     */
    val isActive: Boolean
        get() = !isCompleted

    /**
     * Whether this continuation has been resumed or cancelled.
     */
    val isCompleted: Boolean
        get() = synchronized(this) {
            resumed || cancellationCause != null
        }

    /**
     * Whether this continuation has been cancelled.
     */
    val isCancelled: Boolean
        get() = synchronized(this) {
            cancellationCause != null
        }

    init {
        val job = context[Job]
        // Explicitly check for already having been cancelled.
        // That is, we throw an exception right away, without suspending
        // and going through the wrapped continuation.
        job?.ensureActive()
        // Cancel us if our job gets cancelled.
        job?.invokeOnCancellation { cause ->
            cancel(cause)
        }
    }

    /**
     * Run the specified [handler] if or when this continuation is cancelled.
     *
     * If the continuation is already cancelled, the handler is run
     * immediately; otherwise it is stored for later.
     */
    fun invokeOnCancellation(handler: CancellationHandler) {
        val cause: Throwable = synchronized(this) {
            when {
                cancellationHandler != null ->
                    throw IllegalStateException("Two cancellation handlers")
                cancellationCause != null -> {
                    // We'll run the handler right now.
                    cancellationCause!!
                }
                resumed -> return
                else -> {
                    cancellationHandler = handler
                    return
                }
            }
        }
        handler.handleCancellation(cause)
    }

    /**
     * Cancel this continuation.
     *
     * This method may be called multiple times, including concurrently.
     * It silently does nothing if the continuation is already completed
     * (resumed or cancelled).
     */
    fun cancel(cause: Throwable = CancellationException()) {
        val handler: CancellationHandler? = synchronized(this) {
            // If we're already completed, do nothing.
            if (resumed || cancellationCause != null) {
                return
            }
            cancellationCause = cause
            cancellationHandler
        }
        context[Job]?.invokeOnCancellation(null)
        handler?.handleCancellation(cause)
        wrappedContinuation.resumeWithException(cause)
    }

    private fun throwResumedTwice(): Nothing {
        throw IllegalStateException("Continuation resumed twice")
    }

    /**
     * Resume this continuation or call the given handler.
     *
     * This method lets the caller atomically either resume the continuation,
     * or call the handler if it's already cancelled.
     */
    fun resume(value: T, onCancellation: CancellationHandler) {
        val cause: Throwable? = synchronized(this) {
            when {
                resumed -> throwResumedTwice()
                cancellationCause != null -> cancellationCause!!
                else -> {
                    resumed = true
                    null
                }
            }
        }
        if (cause != null) {
            onCancellation.handleCancellation(cause)
        } else {
            context[Job]?.invokeOnCancellation(null)
            wrappedContinuation.resume(value)
        }
    }

    override fun resumeWith(result: Result<T>) {
        synchronized(this) {
            when {
                resumed -> throwResumedTwice()
                cancellationCause != null -> return
                else -> resumed = true
            }
        }
        context[Job]?.invokeOnCancellation(null)
        wrappedContinuation.resumeWith(result)
    }

    override val context: CoroutineContext
        get() = wrappedContinuation.context
}

/**
 * Suspend this coroutine in a cancellable way.
 *
 * This function suspends the current coroutine similar to [suspendCoroutine].
 * Additionally, it wraps the continuation in a [CancellableContinuation],
 * which implements integration with the cancellation system.
 */
suspend fun <T> suspendCancellableCoroutine(
    block: (CancellableContinuation<T>) -> Unit,
): T = suspendCoroutine<T> { cont ->
    val cancellable = CancellableContinuation(cont)
    block(cancellable)
}

/**
 * Wait until the current coroutine is cancelled.
 *
 * This function suspends until it is cancelled. It never resumes the normal
 * way.
 */
suspend fun awaitCancellation(): Nothing = suspendCancellableCoroutine { }

/**
 * Give other coroutines a chance to run.
 *
 * This function immediately schedules the current coroutine to be resumed
 * later, and gives control back to the dispatcher. This gives other coroutines
 * a chance to run in case the dispatcher is swamped with tasks.
 *
 * It is a good idea to call this function regularly in hot CPU-intensive
 * loops.
 */
suspend fun yield(): Unit = suspendCoroutineUninterceptedOrReturn { cont ->
    CancellableContinuation(cont).intercepted().resume(Unit)
    COROUTINE_SUSPENDED
}