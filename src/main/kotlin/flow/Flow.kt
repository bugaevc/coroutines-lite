package io.github.bugaevc.coroutineslite.flow

/**
 * An asynchronous sequence of values.
 *
 * Flow is the coroutine counterpart to [Sequence].
 *
 * The two primary ways to build a flow are with the [flow]
 * [io.github.bugaevc.coroutineslite.flow.flow] and [channelFlow] builders, or
 * by using a [MutableStateFlow]. A flow can be transformed using the various
 * flow transformers such as [map], [filter], [takeWhile], [zip], [debounce],
 * [merge], and others. Finally, a flow can be collected by calling the
 * [collect] method defined by this interface.
 */
interface Flow<out T> {
    /**
     * Call [FlowCollector.emit] for each element of this flow.
     *
     * This method is the coroutine counterpart to [Sequence.forEach].
     *
     * The `collect` call can suspend while waiting for new elements.
     * Additionally, [FlowCollector.emit] calls can suspend while processing
     * emitted values. This way, the flow and the collector take turns running
     * inside the same coroutine. The flow cannot produce elements faster than
     * the collector can process them. See [collectLatest], [conflate], and
     * [buffer] for some ways to relax this restriction.
     *
     * [FlowCollector.emit] **must** be called in the very same coroutine
     * context as this `collect` call. It is invalid to emit from any different
     * context. (Note that this is a slightly stronger requirement than what
     * `kotlinx.coroutines.flow.Flow` requires.) See [channelFlow] and [flowOn]
     * for ways to run the flow and the collector in different contexts.
     *
     * Any exceptions raised by [FlowCollector.emit] **must** be immediately
     * thrown (or re-thrown) from this `collect` call. It is invalid for a flow
     * to catch an exception originating inside [FlowCollector.emit] and then
     * either continue emitting more values or return successfully.
     */
    suspend fun collect(collector: FlowCollector<T>)
}