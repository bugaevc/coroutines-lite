package io.github.bugaevc.coroutineslite

import kotlin.coroutines.CoroutineContext

interface CoroutineExceptionHandler : CoroutineContext.Element {
    companion object Key : CoroutineContext.Key<CoroutineExceptionHandler>

    fun handleException(context: CoroutineContext, exception: Throwable)
}

// We should be able to use a 'fun interface' instead of a factory function,
// but attempting to do that causes a crash with a weird error about a missing
// method.
fun CoroutineExceptionHandler(
    handler: (
        context: CoroutineContext,
        exception: Throwable
    ) -> Unit
): CoroutineExceptionHandler = object : CoroutineExceptionHandler {
    override val key: CoroutineContext.Key<*>
        get() = CoroutineExceptionHandler.Key

    override fun handleException(
        context: CoroutineContext,
        exception: Throwable
    ) {
        handler(context, exception)
    }
}