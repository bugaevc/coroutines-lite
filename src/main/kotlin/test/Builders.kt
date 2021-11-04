package io.github.bugaevc.coroutineslite.test

import io.github.bugaevc.coroutineslite.DeferredImpl
import io.github.bugaevc.coroutineslite.Job
import kotlin.coroutines.Continuation
import kotlin.coroutines.CoroutineContext
import kotlin.coroutines.EmptyCoroutineContext
import kotlin.coroutines.intrinsics.createCoroutineUnintercepted
import kotlin.coroutines.resume

fun <T> runBlockingTest(
    context: CoroutineContext = EmptyCoroutineContext,
    block: suspend TestCoroutineScope.() -> T,
): T {
    val parentJob: Job? = context[Job]
    val deferred = DeferredImpl<T>(parent = parentJob)
    deferred.cancelParentOnFailure = false
    val dispatcher = TestCoroutineDispatcher()
    val scope = TestCoroutineScope(
        context = dispatcher + context + deferred,
        delayController = dispatcher,
    )
    val continuation = Continuation(scope.coroutineContext, deferred::complete)
    block.createCoroutineUnintercepted(receiver = scope, continuation)
        .resume(Unit)
    return dispatcher.runBlockingTest(EmptyCoroutineContext, deferred::await)
}