package io.github.bugaevc.coroutineslite.flow

import io.github.bugaevc.coroutineslite.channels.BufferOverflow
import io.github.bugaevc.coroutineslite.channels.Channel
import io.github.bugaevc.coroutineslite.channels.ProducerScope
import io.github.bugaevc.coroutineslite.channels.SendChannel
import kotlin.coroutines.EmptyCoroutineContext

/**
 * Build a [Flow] by emitting some elements.
 *
 * This is the most common way to create a flow. [block] becomes the
 * implementation of [Flow.collect]. It can emit values by directly calling
 * `emit(value)`.
 */
fun <T> flow(block: suspend FlowCollector<T>.() -> Unit): Flow<T> {
    return object : Flow<T> {
        override suspend fun collect(collector: FlowCollector<T>) {
            collector.block()
        }
    }
}

/**
 * Build a flow from preexisting values.
 */
fun <T> flowOf(vararg elements: T): Flow<T> = flow {
    for (element in elements) {
        emit(element)
    }
}

/**
 * Create an empty flow that emits no values.
 */
fun <T> emptyFlow(): Flow<T> = flowOf()

/**
 * Build a flow that receives its values from a [Channel].
 *
 * When the flow is collected, [block] will be invoked concurrently with the
 * collector. Any values sent to the [ProducerScope.channel] while the [block]
 * is running will be received and emitted by the flow. When the [block]
 * returns (and all of its child jobs complete), the channel will be closed
 * automatically. If the collector throws an error, the [block] will be
 * cancelled.
 *
 * Unlike with regular flow, where [FlowCollector.emit] must be called from the
 * same context as the [Flow.collect] call itself, it **is** valid to make
 * [SendChannel.send] calls from any context, including making multiple calls
 * concurrently.
 *
 * Use [buffer] and [flowOn] to configure the channel and context of the scope.
 */
fun <T> channelFlow(block: suspend ProducerScope<T>.() -> Unit): Flow<T> {
    return ChannelFlow(
        capacity = null,
        onBufferOverflow = BufferOverflow.SUSPEND,
        context = EmptyCoroutineContext,
        block = block,
    )
}