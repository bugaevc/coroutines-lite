package io.github.bugaevc.coroutineslite.flow

import io.github.bugaevc.coroutineslite.CoroutineScope
import io.github.bugaevc.coroutineslite.Job

/**
 * Collect this flow to a list of values.
 */
suspend fun <T> Flow<T>.toList(
    destination: MutableList<T> = mutableListOf<T>(),
): List<T> {
    collect { value ->
        destination.add(value)
    }
    return destination
}

/**
 * Collect this flow, ignoring emitted values.
 *
 * This is useful when the flow is run purely for its side effects.
 */
suspend fun Flow<*>.collect(): Unit = collect { }

/**
 * Launch collection of this flow in [scope], ignoring emitted values.
 *
 * This is useful when the flow is run purely for its side effects.
 */
fun Flow<*>.launchIn(scope: CoroutineScope): Job {
    return scope.launch {
        collect()
    }
}

/**
 * Collect elements of this flow along with their indexes.
 */
suspend fun <T> Flow<T>.collectIndexed(
    emit: suspend (index: Int, value: T) -> Unit,
) {
    var index = 0
    collect { value ->
        emit(index++, value)
    }
}