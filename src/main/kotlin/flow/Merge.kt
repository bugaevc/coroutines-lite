package io.github.bugaevc.coroutineslite.flow

import io.github.bugaevc.coroutineslite.sync.Semaphore

/**
 * Concurrency level used by default in [flattenMerge] and [flattenMerge].
 */
const val DEFAULT_CONCURRENCY: Int = 16

/**
 * Merge these flows into a single flow.
 *
 * All the flows are collected concurrently, and their values are re-emitted
 * from the resulting flow.
 */
fun <T> Iterable<Flow<T>>.merge(): Flow<T> = channelFlow {
    for (flow in this@merge) {
        launch {
            flow.collect { value ->
                send(value)
            }
        }
    }
}

/**
 * Merge these flows into a single flow.
 *
 * All the flows are collected concurrently, and their values are re-emitted
 * from the resulting flow.
 */
fun <T> merge(vararg flows: Flow<T>): Flow<T> {
    return flows.asIterable().merge()
}

/**
 * Apply the given [transformation][transform] to each element of this flow,
 * merging the resulting subflows, as if with [merge].
 *
 * At most [concurrency] (which defaults to [DEFAULT_CONCURRENCY]) subflows are
 * concurrently collected at the same time.
 *
 * @see flatMapConcat
 * @see flatMapLatest
 */
fun <T, R> Flow<T>.flatMapMerge(
    concurrency: Int = DEFAULT_CONCURRENCY,
    transform: suspend (T) -> Flow<R>,
): Flow<R> = channelFlow {
    val semaphore = Semaphore(permits = concurrency)
    collect { value: T ->
        val subflow: Flow<R> = transform(value)
        // Wait for one of the previous subflows to complete.
        semaphore.acquire()
        launch {
            try {
                subflow.collect { item: R ->
                    send(item)
                }
            } finally {
                // Let someone else in.
                semaphore.release()
            }
        }
    }
}

/**
 * Flatten this flow of subflows by merging subflows, as if with [merge].
 *
 * At most [concurrency] (which defaults to [DEFAULT_CONCURRENCY]) subflows are
 * concurrently collected at the same time.
 *
 * @see flattenConcat
 * @see flatMapMerge
 */
fun <T> Flow<Flow<T>>.flattenMerge(
    concurrency: Int = DEFAULT_CONCURRENCY,
): Flow<T> = flatMapMerge(concurrency) { subflow -> subflow }