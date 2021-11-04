package io.github.bugaevc.coroutineslite.channels

import io.github.bugaevc.coroutineslite.suspendCancellableCoroutine
import java.util.*
import kotlin.coroutines.Continuation
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

/**
 * A fundamental cross-coroutine communication primitive.
 *
 * A channel is a queue of elements. Elements can be sent (enqueued) to a
 * channel by calling [send], and received (dequeued) from it by calling
 * [receive].
 *
 * Any number of coroutines can call [send] and [receive] concurrently without
 * any additional synchronization. Specifically, there's no requirement that
 * sending and receiving an element has to be done from the same coroutine
 * context. An element sent by a sender coroutine can be received by any of the
 * receiver coroutines.
 *
 * Once there are no more elements to be sent, a channel can be closed by
 * calling [close].
 *
 * A channel stores sent but not yet received elements in a buffer, whose
 * [capacity] (maximum size) is determined when the channel is created. If a
 * sender attempts to send an element when the buffer is full, either the
 * sender gets suspended until one of the slots frees up, or one of the
 * elements is dropped, depending on the configured [buffer overflow handling
 * strategy][onBufferOverflow].
 */
class Channel<E>(
    private val capacity: Int = RENDEZVOUS,
    private val onBufferOverflow: BufferOverflow = BufferOverflow.SUSPEND,
) : SendChannel<E>, ReceiveChannel<E> {

    companion object {
        /**
         * Unlimited buffer capacity: the buffer can grow arbitrarily large.
         *
         * Using unlimited capacity is discouraged, because it fails to
         * propagate backpressure.
         */
        val UNLIMITED: Int = Int.MAX_VALUE

        /**
         * Rendezvous buffer capacity: no buffering at all.
         *
         * When using this capacity, a sender and a receiver have to rendezvous
         * with each other for the transfer of the element to happen. That
         * means whichever one comes first will suspend, waiting for the other
         * one to appear.
         *
         * This means that the senders will produce new elements at the rate
         * the receivers can consume them, but no faster.
         */
        val RENDEZVOUS: Int = 0

        /**
         * A generally reasonable buffer capacity.
         */
        val BUFFERED: Int = 64
    }

    private var closeCause: Throwable? = null
    private var closeHandler: CloseHandler? = null
    private val buffer = LinkedList<E>()
    private val senders = mutableListOf<Continuation<Unit>>()
    private val receivers = mutableListOf<Continuation<ChannelResult<E>>>()

    override var isClosedForSend: Boolean = false
        private set

    override val isClosedForReceive: Boolean
        get() = synchronized(this) {
            isClosedForSend && buffer.isEmpty()
        }

    override suspend fun send(element: E) {
        var done = false
        while (!done) {
            suspendCancellableCoroutine<Unit> { cont ->
                synchronized(this) {
                    val res = trySend(element)
                    when {
                        res.isSuccess -> {
                            done = true
                            cont.resume(Unit)
                            return@suspendCancellableCoroutine
                        }
                        res.isClosed -> {
                            done = true
                            // trySend() always returns an exception.
                            val ex = res.exceptionOrNull()!!
                            cont.resumeWithException(ex)
                            return@suspendCancellableCoroutine
                        }
                        else -> {
                            senders.add(cont)
                            cont.invokeOnCancellation {
                                synchronized(this) {
                                    senders.remove(cont)
                                }
                            }
                        }
                    }
                }
            }
        }
    }

    override fun trySend(element: E): ChannelResult<Unit> {
        synchronized(this) {
            return when {
                isClosedForSend -> {
                    val ex = closeCause ?: ClosedSendChannelException()
                    ChannelResult.closed(ex)
                }
                // Try directly handing off the element to a receiver.
                buffer.isEmpty() && receivers.isNotEmpty() -> {
                    val success = ChannelResult.success(element)
                    receivers.removeFirst().resume(success)
                    ChannelResult.success(Unit)
                }
                // Try putting the element into the buffer.
                buffer.size < capacity -> {
                    buffer.add(element)
                    ChannelResult.success(Unit)
                }
                // Otherwise, let's see how we can handle overflow.
                else -> when (onBufferOverflow) {
                    BufferOverflow.DROP_LATEST -> ChannelResult.success(Unit)
                    BufferOverflow.DROP_OLDEST -> {
                        buffer.remove()
                        buffer.add(element)
                        ChannelResult.success(Unit)
                    }
                    BufferOverflow.SUSPEND -> ChannelResult.failure()
                }
            }
        }
    }

    override fun close(cause: Throwable?): Boolean {
        val r: Array<Continuation<ChannelResult<E>>>
        val handler: CloseHandler?
        synchronized(this) {
            if (isClosedForSend) {
                return false
            }
            isClosedForSend = true
            closeCause = cause
            r = receivers.toTypedArray()
            receivers.clear()
            handler = closeHandler
            closeHandler = null
        }
        // If there are any receivers, wake them all now.
        if (r.isNotEmpty()) {
            assert(buffer.isEmpty())
            for (receiver in r) {
                receiver.resume(ChannelResult.closed(cause))
            }
        }
        handler?.handleClose(cause)
        return true
    }

    override fun invokeOnClose(handler: CloseHandler) {
        val invokeNow: Boolean
        val cause: Throwable?
        synchronized(this) {
            if (closeHandler != null) {
                throw IllegalStateException("Two close handlers")
            }
            invokeNow = isClosedForSend
            cause = closeCause
            if (!invokeNow) {
                closeHandler = handler
            }
        }
        if (invokeNow) {
            handler.handleClose(cause)
        }
    }

    override val isEmpty: Boolean
        get() = synchronized(this) {
            buffer.isEmpty()
        }

    override fun tryReceive(): ChannelResult<E> {
        return synchronized(this) {
            when {
                // Try to take an element from the buffer.
                buffer.isNotEmpty() -> {
                    val element: E = buffer.remove()
                    // If there are suspended senders (implying we are or
                    // recently were at the maximum capacity), wake one now,
                    // since there is now a free buffer slot.
                    senders.removeFirstOrNull()?.resume(Unit)
                    ChannelResult.success(element)
                }
                // There's nothing in the buffer; if we're closed for sending,
                // then we're also closed for receiving.
                isClosedForSend -> ChannelResult.closed(closeCause)
                else -> ChannelResult.failure()
            }
        }
    }

    override suspend fun receiveCatching(): ChannelResult<E> {
        return suspendCancellableCoroutine { cont ->
            synchronized(this) {
                val res = tryReceive()
                when {
                    res.isSuccess || res.isClosed -> cont.resume(res)
                    else -> {
                        // Can't receive now, we'll have to wait for it.
                        receivers.add(cont)
                        cont.invokeOnCancellation {
                            synchronized(this) {
                                receivers.remove(cont)
                            }
                        }
                        // Wake up a sender, if they are waiting
                        // for us to rendezvous.
                        senders.removeFirstOrNull()?.resume(Unit)
                    }
                }
            }
        }
    }

    override suspend fun receive(): E {
        val res = receiveCatching()
        val element = res.getOrNull()
        if (element != null) {
            return element
        }
        throw res.exceptionOrNull() ?: ClosedReceiveChannelException()
    }
}