package io.github.bugaevc.coroutineslite.flow

fun <T> Flow<T>.onStart(
    action: suspend FlowCollector<T>.() -> Unit,
): Flow<T> = flow {
    action()
    emitAll(this@onStart)
}

fun <T> Flow<T>.onCompletion(
    action: suspend FlowCollector<T>.(Throwable?) -> Unit,
): Flow<T> = flow {
    var exception: Throwable? = null
    try {
        emitAll(this@onCompletion)
    } catch (ex: Throwable) {
        exception = ex
    } finally {
        action(exception)
    }
}