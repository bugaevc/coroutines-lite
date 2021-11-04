package io.github.bugaevc.coroutineslite.flow

import io.github.bugaevc.coroutineslite.CoroutineScope
import io.github.bugaevc.coroutineslite.DeferredImpl
import io.github.bugaevc.coroutineslite.Job
import io.github.bugaevc.coroutineslite.channels.BufferOverflow
import io.github.bugaevc.coroutineslite.channels.Channel
import io.github.bugaevc.coroutineslite.channels.ProducerScope
import io.github.bugaevc.coroutineslite.channels.ReceiveChannel
import kotlin.coroutines.*

/**
 * A flow that runs [block], which sends values to a channel,
 * and emits values as they are received from the channel.
 *
 * This is the internal implementation behind the [channelFlow] builder,
 * as well as [flowOn], [buffer], [conflate], [produceIn], [mapLatest],
 * [flatMapLatest], [transformLatest], [merge], [flatMapMerge], and
 * [flattenMerge] functions.
 *
 * An important property of `ChannelFlow` is that subsequent transformations
 * using `ChannelFlow` are *fused*. That is, applying several such
 * transformations in a row will not create multiple channels forwarding values
 * to each other in a chain. Instead, a single `ChannelFlow` (and a single
 * channel) will be created, whose parameters will be adjusted to behave as if
 * each operation was applied separately. See [makeChannelFlow].
 */
internal class ChannelFlow<T>(
    private val capacity: Int?,
    private val onBufferOverflow: BufferOverflow,
    private val context: CoroutineContext,
    private val block: suspend ProducerScope<T>.() -> Unit,
) : Flow<T> {

    init {
        // The context may specify things like a dispatcher and an exception
        // handler, but not a job. We'll create our own job as a child of the
        // job where our collect() is called.
        if (context[Job] != null) {
            throw IllegalArgumentException("ChannelFlow context has a job")
        }
    }

    /**
     * This is the underlying implementation of [produceIn].
     *
     * It launches [block] as a child job of the job contained in the
     * [outerContext]. This new job will *not* cancel its parent on failure,
     * instead it propagates exceptions by closing the channel.
     */
    internal fun doProduceIn(
        outerContext: CoroutineContext,
    ): Pair<ReceiveChannel<T>, Job> {
        val channel = Channel<T>(
            capacity ?: Channel.BUFFERED,
            onBufferOverflow,
        )
        val deferred = DeferredImpl<Unit>(parent = outerContext[Job])
        deferred.cancelParentOnFailure = false
        val combinedContext = outerContext + this.context + deferred
        val scope = ProducerScope<T>(combinedContext, channel)
        val continuation = Continuation<Unit>(
            context = combinedContext,
            resumeWith = deferred::complete,
        )
        // We'll launch the call to await() separately. This is because we only
        // want to close the channel once the job is fully completed (including
        // all of its children). We also want to propagate any exceptions from
        // its children to the channel closing cause.
        val awaitContinuation = Continuation<Unit>(EmptyCoroutineContext) {
            channel.close(it.exceptionOrNull())
        }
        // Launch both the block and the call to await().
        block.startCoroutine(
            receiver = scope,
            completion = continuation,
        )
        deferred::await.startCoroutine(completion = awaitContinuation)
        return channel to deferred
    }

    override suspend fun collect(collector: FlowCollector<T>) {
        val (channel, job) = doProduceIn(coroutineContext)
        try {
            // Loop, receiving values from the channel.
            while (true) {
                val result = channel.receiveCatching()
                if (result.isClosed) {
                    // If the block (or one of its child jobs) fails, we close
                    // the channel with an error. Re-throw such errors.
                    when (val cause = result.exceptionOrNull()) {
                        null -> break
                        else -> throw cause
                    }
                }
                collector.emit(result.getOrThrow())
            }
        } catch (ex: Throwable) {
            // We may get errors from the collector; make sure we cancel the
            // job in that case. It will do nothing if the job is already
            // cancelled, and we're just propagating that error.
            job.cancel(ex)
            throw ex
        } finally {
            // Either way, wait for the job to fully complete before we return.
            job.join()
        }
    }

    private val BufferOverflow.neverSuspends: Boolean
        get() = this != BufferOverflow.SUSPEND

    internal fun fuse(
        capacity: Int?,
        onBufferOverflow: BufferOverflow,
        context: CoroutineContext,
    ): ChannelFlow<T> {
        val (newCapacity, newOnBufferOverflow) = when {
            // If they have a specific non-suspending policy,
            // and we don't, respect that.
            !this.onBufferOverflow.neverSuspends &&
                onBufferOverflow.neverSuspends ->
                (capacity ?: this.capacity) to onBufferOverflow
            // We do not care about capacity.
            this.capacity == null -> capacity to this.onBufferOverflow
            // They don't care about capacity.
            capacity == null -> this.capacity to this.onBufferOverflow
            this.onBufferOverflow.neverSuspends -> when {
                !onBufferOverflow.neverSuspends ->
                    this.capacity to this.onBufferOverflow
                // The values get dropped on our side first.
                this.capacity <= capacity ->
                    this.capacity to this.onBufferOverflow
                // The values get dropped on their side first.
                else -> capacity to onBufferOverflow
            }
            // Both sides suspend, and both care about capacity.
            else -> maxOf(this.capacity, capacity) to onBufferOverflow
        }
        // The context specified earlier (closer to the upstream flow) takes
        // precedence over the one specified later. The + operator on contexts
        // works the other way around, values specified later take precedence
        // over the one specified earlier, hence the seemingly reversed order
        // below.
        return ChannelFlow(
            capacity = newCapacity,
            onBufferOverflow = newOnBufferOverflow,
            context = context + this.context,
            block = block,
        )
    }
}

/**
 * Apply the given capacity, overflow handling strategy, and context
 * to this flow, turning it into a [ChannelFlow].
 */
internal fun <T> Flow<T>.makeChannelFlow(
    capacity: Int? = null,
    onBufferOverflow: BufferOverflow = BufferOverflow.SUSPEND,
    context: CoroutineContext = EmptyCoroutineContext,
) = when (this) {
    // If it's already a channel flow, just fuse it!
    is ChannelFlow -> fuse(capacity, onBufferOverflow, context)
    // Create a new channel flow and forward our values to it.
    else -> ChannelFlow(capacity, onBufferOverflow, context) {
        collect { value ->
            send(value)
        }
    }
}

/**
 * Run this flow in the specified context.
 *
 * The returned flow can be collected form a different context; the values
 * emitted by the upstream flow will be sent to the collector through a
 * channel.
 */
fun <T> Flow<T>.flowOn(context: CoroutineContext): Flow<T> =
    makeChannelFlow(context = context)

/**
 * Run this flow concurrently with the collector, and buffer emitted values.
 *
 * To propagate backpressure, the flow is suspended if the collector cannot keep
 * up with it. Specific [capacity] and [onBufferOverflow] values can be
 * specified to configure this behavior.
 *
 * A specific case of buffering is [conflate].
 */
fun <T> Flow<T>.buffer(
    capacity: Int = Channel.BUFFERED,
    onBufferOverflow: BufferOverflow = BufferOverflow.SUSPEND,
): Flow<T> = makeChannelFlow(capacity, onBufferOverflow)

/**
 * Run this flow concurrently with the collector, and only keep the latest
 * emitted value.
 *
 * If the collector cannot keep up with this flow emitting new values, old
 * values are silently dropped. The collector always sees the latest emitted
 * value. The flow is never suspended when it calls [FlowCollector.emit].
 *
 * @see collectLatest
 */
fun <T> Flow<T>.conflate(): Flow<T> =
    buffer(capacity = 1, BufferOverflow.DROP_OLDEST)

/**
 * Launch a job in the [scope] collecting this flow and sending the values to
 * a channel, which is returned.
 *
 * The channel is closed when the flow completes. On failure, the scope is
 * *not* cancelled, instead the channel is closed with the error.
 */
fun <T> Flow<T>.produceIn(scope: CoroutineScope): ReceiveChannel<T> =
    makeChannelFlow().doProduceIn(scope.coroutineContext).first