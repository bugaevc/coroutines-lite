package io.github.bugaevc.coroutineslite.channels

/**
 * The send side of a [Channel].
 */
interface SendChannel<in E> {
    /**
     * Whether no new elements can be sent through this channel.
     *
     * When closed for send, there may still be already enqueued elements in
     * the channel, in which case [ReceiveChannel.isClosedForReceive] will
     * still return false.
     */
    val isClosedForSend: Boolean

    /**
     * Send the [element] through this channel.
     *
     * A call to this method will only suspend if there's no receiver waiting
     * to receive, the [buffer][Channel.capacity] is full, and the buffer
     * overflow handling strategy is set to [BufferOverflow.SUSPEND].
     *
     * If the channel is already [closed for send][isClosedForSend], a call to
     * this method will throw the `cause` exception.
     *
     * @see trySend
     */
    suspend fun send(element: E)

    /**
     * Try to send the [element] through this channel, without suspending.
     *
     * The returned [ChannelResult] describes one of the three cases:
     * * The [element] was sent successfully.
     * * Failed to send the element because the [buffer][Channel.capacity] is
     *   full, and the buffer overflow handling strategy is set to
     *   [BufferOverflow.SUSPEND].
     * * Failed to send the element because the channel is [closed for send]
     *   [isClosedForSend].
     */
    fun trySend(element: E): ChannelResult<Unit>

    /**
     * Close the channel for send, optionally providing a [cause].
     */
    fun close(cause: Throwable? = null): Boolean

    /**
     * Run the [handler] when this channel is closed for send.
     *
     * If the channel is already closed for send, run it right now.
     */
    fun invokeOnClose(handler: CloseHandler)
}