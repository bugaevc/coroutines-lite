package io.github.bugaevc.coroutineslite.channels

import io.github.bugaevc.coroutineslite.CoroutineScope
import io.github.bugaevc.coroutineslite.Job
import io.github.bugaevc.coroutineslite.suspendCancellableCoroutine
import kotlin.coroutines.*

class ProducerScope<in E> internal constructor(
    context: CoroutineContext,
    val channel: SendChannel<E>,
) : CoroutineScope(context), SendChannel<E> by channel {

    suspend fun awaitClose() {
        suspendCancellableCoroutine<Unit> { cont ->
            invokeOnClose {
                // Note: don't propagate the exception.
                cont.resume(Unit)
            }
        }
    }
}


fun <E> CoroutineScope.produce(
    context: CoroutineContext = EmptyCoroutineContext,
    capacity: Int = Channel.RENDEZVOUS,
    block: suspend ProducerScope<E>.() -> Unit,
): ReceiveChannel<E> {
    val newContext = newCoroutineContext(context)
    val job = Job(parent = newContext[Job])
    job.cancelParentOnFailure = false
    val combinedContext = newContext + job
    val channel = Channel<E>(capacity)
    val continuation = Continuation<Unit>(combinedContext) { res ->
        try {
            channel.close(res.exceptionOrNull())
        } finally {
            job.completeWith(res)
        }
    }
    val producerScope = ProducerScope<E>(
        context = combinedContext,
        channel = channel,
    )
    block.startCoroutine(producerScope, completion = continuation)
    return channel
}