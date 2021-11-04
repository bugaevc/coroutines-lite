package io.github.bugaevc.coroutineslite.channels

/**
 * The receive side of a [Channel].
 */
interface ReceiveChannel<out E> {
    /**
     * Whether no new elements can be received from this channel.
     *
     * A channel becomes closed for receive once it is [closed for send]
     * [SendChannel.isClosedForSend], and all the elements enqueued prior to
     * that are received.
     */
    val isClosedForReceive: Boolean

    /**
     * Whether this channel's buffer is empty.
     *
     * If this is true, a call to [receive] will not suspend (in absence of
     * other coroutines that concurrently attempt to receive the same value).
     *
     * When this channel is [closed for receive][isClosedForReceive], it's
     * always empty.
     */
    val isEmpty: Boolean

    /**
     * Receive an element from this channel.
     *
     * A call to this method will suspend if the buffer [is empty][isEmpty] and
     * no sender is waiting to [send][SendChannel.send]. If the channel is
     * [closed for receive][isClosedForReceive], this method will throw the
     * `cause` exception.
     *
     * @see receiveCatching
     * @see tryReceive
     */
    suspend fun receive(): E

    /**
     * Receive an element from this channel, without failing if it's closed.
     *
     * The method behaves much like [receive], except that in case the channel
     * is [closed for receive][isClosedForReceive], it will not throw an
     * exception. Instead, it returns a [ChannelResult] either containing the
     * received element, or describing the closed channel.
     */
    suspend fun receiveCatching(): ChannelResult<E>

    /**
     * Try to receive an element from this channel, without suspending or
     * failing.
     *
     * The returned [ChannelResult] describes one of the three cases:
     * * An element was received successfully.
     * * Failed to receive an element because the buffer [is empty][isEmpty],
     *   and there were no suspended senders waiting to send.
     * * Failed to receive an element because the channel is [closed for
     *   receive][isClosedForReceive].
     */
    fun tryReceive(): ChannelResult<E>
}