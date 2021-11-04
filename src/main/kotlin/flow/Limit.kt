package io.github.bugaevc.coroutineslite.flow

import kotlin.coroutines.cancellation.CancellationException

internal class AbortFlowException :
    CancellationException("No more items required")

/**
 * Skip first [count] elements of this flow, then emit the rest.
 */
fun <T> Flow<T>.drop(count: Int): Flow<T> = flow {
    var seen = 0
    collect { value ->
        when {
            seen >= count -> emit(value)
            else -> seen++
        }
    }
}

/**
 * Skip elements of this flow while they match the [predicate].
 */
fun <T> Flow<T>.dropWhile(predicate: suspend (T) -> Boolean): Flow<T> = flow {
    var needToCheck = true
    collect { value ->
        when {
            !needToCheck -> emit(value)
            !predicate(value) -> {
                needToCheck = false
                emit(value)
            }
        }
    }
}

private suspend fun <T> Flow<T>.collectWhile(emit: suspend (T) -> Boolean) {
    var exception: AbortFlowException? = null
    try {
        collect { value ->
            val needMore = emit(value)
            if (!needMore) {
                val ex = AbortFlowException()
                exception = ex
                throw ex
            }
        }
    } catch (ex: AbortFlowException) {
        // Make sure it is our exception, and re-throw it if it's not.
        if (ex !== exception) {
            throw ex
        }
    }
}

/**
 * Take [count] of elements from this flow, then cancel the upstream flow.
 */
fun <T> Flow<T>.take(count: Int): Flow<T> = flow {
    if (count <= 0) {
        return@flow
    }
    var seen = 0
    collectWhile { value ->
        emit(value)
        ++seen < count
    }
}

/**
 * Take elements from this flow while they match the [predicate],
 * then cancel the upstream flow.
 */
fun <T> Flow<T>.takeWhile(predicate: suspend (T) -> Boolean): Flow<T> = flow {
    collectWhile { value ->
        val matches = predicate(value)
        if (matches) {
            emit(value)
        }
        matches
    }
}