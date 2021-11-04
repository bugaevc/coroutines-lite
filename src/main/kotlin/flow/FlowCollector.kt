package io.github.bugaevc.coroutineslite.flow

/**
 * Callback for [Flow.collect].
 *
 * A flow will call [emit] for each element of the flow.
 */
fun interface FlowCollector<in T> {
    suspend fun emit(value: T)
}

/**
 * Emit all the elements from the [flow] into this collector.
 */
suspend fun <T> FlowCollector<T>.emitAll(flow: Flow<T>) {
    flow.collect { item ->
        emit(item)
    }
}