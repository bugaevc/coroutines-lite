package io.github.bugaevc.coroutineslite.flow

/**
 * Filter this flow, leaving only elements that satisfy the given [predicate].
 *
 * This will return a new flow which applies [predicate] to each item of the
 * original flow, and only re-emits those items that satisfy the predicate.
 *
 * @see filterNonNull
 * @see filterIsInstance
 */
fun <T> Flow<T>.filter(predicate: suspend (T) -> Boolean): Flow<T> = flow {
    collect { value ->
        if (predicate(value)) {
            emit(value)
        }
    }
}

/**
 * Filter out null elements.
 *
 * This is a specialized version of [filter], which filters out null elements
 * form this flow. The resulting flow emits items of type `T`, not `T?`.
 */
fun <T> Flow<T?>.filterNonNull(): Flow<T> = flow {
    collect { value ->
        if (value != null) {
            emit(value)
        }
    }
}

/**
 * Filter out elements that are not instances of the given class.
 *
 * This is a specialized version of [filter] which only keeps elements which
 * are instances of the specified type [R]. The resulting flow emits values
 * of type [R].
 */
inline fun <reified R> Flow<*>.filterIsInstance(): Flow<R> = flow {
    collect { value ->
        if (value is R) {
            emit(value)
        }
    }
}

/**
 * Apply the given [transformation][transform] to each element of this flow.
 *
 * This will return a new flow which applies [transform] to each item of the
 * original flow, and emits the results.
 */
fun <T, R> Flow<T>.map(transform: suspend (T) -> R): Flow<R> = flow {
    collect { value ->
        emit(transform(value))
    }
}

/**
 * Run the given [action] on each element, then pass it on unchanged.
 *
 * This will return a new flow which runs [action] on each element of the
 * original flow, and then re-emits the same values.
 */
fun <T> Flow<T>.onEach(action: suspend (T) -> Unit): Flow<T> = flow {
    collect { value ->
        action(value)
        emit(value)
    }
}

/**
 * Flatten a flow of subflows by concatenating subflows in order.
 *
 * In the outer flow, [FlowCollector.emit] calls will only return once the
 * emitted subflow is itself fully collected.
 *
 * @see flattenMerge
 * @see flatMapConcat
 */
fun <T> Flow<Flow<T>>.flattenConcat(): Flow<T> = flow {
    collect { subflow ->
        emitAll(subflow)
    }
}

/**
 * Apply the given [transformation][transform] to each element of this flow,
 * concatenating the resulting subflows.
 *
 * @see flatMapMerge
 * @see flatMapLatest
 */
fun <T, R> Flow<T>.flatMapConcat(
    transform: suspend (T) -> Flow<R>,
): Flow<R> = flow {
    collect { value: T ->
        emitAll(transform(value))
    }
}

/**
 * Filter out equal consecutive values from this flow.
 *
 * If the original flow emits the same value (or equal values) multiple times
 * in a row, the new flow will only emit it once.
 */
fun <T> Flow<T>.distinctUntilChanged(): Flow<T> = flow {
    var previous: T? = null
    var havePrevious = false

    collect { value ->
        if (!havePrevious || previous != value) {
            havePrevious = true
            previous = value
            emit(value)
        }
    }
}