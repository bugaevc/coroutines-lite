package io.github.bugaevc.coroutineslite.flow

import io.github.bugaevc.coroutineslite.CoroutineScope
import io.github.bugaevc.coroutineslite.Job
import io.github.bugaevc.coroutineslite.coroutineScope

/**
 * The internal version of [collectLatest] that spawns jobs into the provided
 * [scope]. Note: can leave a job still running in [scope] after it returns!
 */
private suspend fun <T> Flow<T>.collectLatestIn(
    scope: CoroutineScope,
    collector: FlowCollector<T>,
) {
    var emitJob: Job? = null
    collect { value ->
        emitJob?.cancelAndJoin()
        emitJob = scope.launch {
            collector.emit(value)
        }
    }
}

/**
 * Collect this flow, running it concurrently with the collector.
 *
 * If the flow emits a new value before the collector finishes processing the
 * previous one, the previous [FlowCollector.emit] call is cancelled. Unlike
 * with the regular [Flow.collect] call, [FlowCollector.emit] will *not* be
 * invoked in the exact same coroutine context that this function is called in.
 *
 * @see conflate
 */
suspend fun <T> Flow<T>.collectLatest(collector: FlowCollector<T>) {
    coroutineScope {
        collectLatestIn(scope = this, collector)
    }
}

/**
 * Run this flow concurrently with applying [transform] to its values.
 *
 * If the flow emits a new value before transforming the previous value is
 * completed, transforming the previous value is cancelled, and the value is
 * skipped.
 */
fun <T, R> Flow<T>.mapLatest(
    transform: suspend (T) -> R,
): Flow<R> = channelFlow {
    collectLatestIn(scope = this) { value ->
        val transformed = transform(value)
        send(transformed)
    }
}

/**
 * Apply the given [transformation][transform] to each element of this flow,
 * only emitting elements from the latest resulting subflow.
 *
 * The original flow is collected concurrently with applying [transform] to its
 * latest element and collecting the resulting subflow. Only a single subflow
 * is collected at a time; if the original flow emits a new element, collecting
 * the previous subflow is cancelled.
 *
 * @see flatMapConcat
 * @see flatMapMerge
 */
fun <T, R> Flow<T>.flatMapLatest(
    transform: suspend (T) -> Flow<R>,
): Flow<R> = channelFlow {
    collectLatestIn(scope = this) { value: T ->
        val subflow: Flow<R> = transform(value)
        subflow.collect { item: R ->
            send(item)
        }
    }
}

fun <T, R> Flow<T>.transformLatest(
    transform: suspend FlowCollector<R>.(T) -> Unit,
): Flow<R> = channelFlow {
    val collector = FlowCollector<R> { value: R ->
        send(value)
    }
    collectLatestIn(scope = this) { value: T ->
        collector.transform(value)
    }
}