package io.github.bugaevc.coroutineslite

import kotlin.coroutines.Continuation
import kotlin.coroutines.CoroutineContext
import kotlin.coroutines.cancellation.CancellationException
import kotlin.coroutines.resume

open class Job(
    val parent: Job? = null,
) : CoroutineContext.Element {

    companion object Key : CoroutineContext.Key<Job>
    override val key: CoroutineContext.Key<*> get() = Job

    private var workCompleted: Boolean = false
    protected var cancellationCause: Throwable? = null
    private var cancellationHandler: CancellationHandler? = null
    internal var cancelParentOnFailure: Boolean = true
    private val children = mutableListOf<Job>()
    private val joiners = mutableListOf<Continuation<Unit>>()

    val isActive: Boolean
        get() = synchronized(this) {
            !isCancelled && (!workCompleted || children.isNotEmpty())
        }

    val isCompleted: Boolean
        get() = synchronized(this) {
            workCompleted && children.isEmpty()
        }

    val isCancelled: Boolean
        get() = synchronized(this) {
            cancellationCause != null
        }

    private fun attachChild(child: Job) {
        val cause: Throwable? = synchronized(this) {
            if (cancellationCause == null) {
                children.add(child)
            }
            cancellationCause
        }
        // Cancel the child immediately if we're already cancelled.
        if (cause != null) {
            child.cancel(cause)
        }
    }

    init {
        @Suppress("LeakingThis")
        parent?.attachChild(this)
    }

    private fun tryFinishing() {
        val j: Array<Continuation<Unit>>
        synchronized(this) {
            if (!isCompleted) {
                return
            }
            j = joiners.toTypedArray()
            joiners.clear()
        }
        parent?.detachChild(this)
        for (joiner in j) {
            joiner.resume(Unit)
        }
    }

    private fun detachChild(child: Job) {
        synchronized(this) {
            children.remove(child)
        }
        tryFinishing()
    }

    fun cancel(cause: Throwable = CancellationException()) {
        val ch: Array<Job>
        val handler: CancellationHandler?
        synchronized(this) {
            if (cancellationCause != null) {
                // Already cancelled.
                return
            }
            cancellationCause = cause
            ch = children.toTypedArray()
            handler = cancellationHandler
        }
        handler?.handleCancellation(cause)
        for (child in ch) {
            child.cancel(cause)
        }
        tryFinishing()
    }

    fun complete() {
        synchronized(this) {
            workCompleted = true
        }
        tryFinishing()
    }

    protected open fun childCancelled(cause: Throwable) {
        if (cause !is CancellationException) {
            cancel(cause)
        }
    }

    fun completeExceptionally(exception: Throwable) {
        val ch: Array<Job>
        val handler: CancellationHandler?
        synchronized(this) {
            workCompleted = true
            if (cancellationCause == null) {
                cancellationCause = exception
                handler = cancellationHandler
            } else {
                handler = null
            }
            ch = children.toTypedArray()
        }
        handler?.handleCancellation(exception)
        for (child in ch) {
            child.cancel(exception)
        }
        // Propagate the exception to our parent, if we have any.
        // If we don't, whoever created us should pass the exception
        // to a CoroutineExceptionHandler.
        if (cancelParentOnFailure) {
            parent?.childCancelled(exception)
        }
        tryFinishing()
    }

    fun completeWith(result: Result<Unit>) {
        when (val ex = result.exceptionOrNull()) {
            null -> complete()
            else -> completeExceptionally(ex)
        }
    }

    suspend fun join() {
        suspendCancellableCoroutine<Unit> { cont ->
            val shouldResume = synchronized(this) {
                if (isCompleted) {
                    true
                } else {
                    joiners.add(cont)
                    cont.invokeOnCancellation {
                        synchronized(this) {
                            joiners.remove(cont)
                        }
                    }
                    false
                }
            }
            if (shouldResume) {
                cont.resume(Unit)
            }
        }
    }

    suspend fun cancelAndJoin(cause: Throwable = CancellationException()) {
        cancel(cause)
        join()
    }

    fun ensureActive() {
        if (!isActive) {
            throw cancellationCause ?: CancellationException()
        }
    }

    /**
     * Run the specified [handler] if or when this job is cancelled.
     *
     * If the job is already cancelled, the handler is run immediately;
     * otherwise it is stored for later.
     */
    fun invokeOnCancellation(handler: CancellationHandler?) {
        val cause: Throwable = synchronized(this) {
            when {
                handler != null && cancellationHandler != null ->
                    throw IllegalStateException("Two cancellation handlers")
                cancellationCause != null -> {
                    // We'll run the handler right now.
                    cancellationCause!!
                }
                else -> {
                    cancellationHandler = handler
                    return
                }
            }
        }
        handler?.handleCancellation(cause)
    }
}

suspend fun Collection<Job>.joinAll() {
    for (job in this) {
        job.join()
    }
}