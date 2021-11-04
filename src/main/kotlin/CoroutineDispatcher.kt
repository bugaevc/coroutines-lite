package io.github.bugaevc.coroutineslite

import kotlin.coroutines.AbstractCoroutineContextElement
import kotlin.coroutines.Continuation
import kotlin.coroutines.ContinuationInterceptor
import kotlin.coroutines.CoroutineContext

abstract class CoroutineDispatcher :
    ContinuationInterceptor,
    AbstractCoroutineContextElement(ContinuationInterceptor)
{
    abstract fun dispatch(context: CoroutineContext, runnable: Runnable)

    override fun <T> interceptContinuation(
        continuation: Continuation<T>
    ): Continuation<T> {
        return DispatchedContinuation(continuation, this)
    }
}

private class DispatchedContinuation<T>(
    private val wrappedContinuation: Continuation<T>,
    private val dispatcher: CoroutineDispatcher,
) : Continuation<T> {

    override val context: CoroutineContext
        get() = wrappedContinuation.context + dispatcher

    override fun resumeWith(result: Result<T>) {
        dispatcher.dispatch(context) {
            wrappedContinuation.resumeWith(result)
        }
    }
}