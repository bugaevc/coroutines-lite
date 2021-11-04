package io.github.bugaevc.coroutineslite

import kotlin.coroutines.*
import kotlin.coroutines.intrinsics.createCoroutineUnintercepted

suspend fun <T> withContext(
    context: CoroutineContext,
    block: suspend () -> T
): T = suspendCoroutine { cont ->
    val newContext = cont.context + context
    val newContinuation = Continuation(newContext, cont::resumeWith)
    block.startCoroutine(newContinuation)
}

fun <T> runBlocking(
    context: CoroutineContext = EmptyCoroutineContext,
    block: suspend CoroutineScope.() -> T,
): T {
    val parentJob: Job? = context[Job]
    val deferred = DeferredImpl<T>(parent = parentJob)
    deferred.cancelParentOnFailure = false
    // Provide a dispatcher in the (common) case the context doesn't
    // have one already.
    val scope = CoroutineScope(Dispatchers.Default + context + deferred)
    val continuation = Continuation(scope.coroutineContext, deferred::complete)
    // Find an event loop we're going to use. Try to use one from
    // the context, or use the default one otherwise.
    val eventLoop: EventLoop =
        scope.coroutineContext[ContinuationInterceptor] as? EventLoop ?:
        (Dispatchers.Default as EventLoop)
    block.createCoroutineUnintercepted(receiver = scope, continuation)
        .resume(Unit)
    return eventLoop.runBlocking(EmptyCoroutineContext, deferred::await)
}

suspend fun <R> coroutineScope(block: suspend CoroutineScope.() -> R): R {
    val parentJob = coroutineContext[Job]
    val deferred = DeferredImpl<R>(parent = parentJob)
    deferred.cancelParentOnFailure = false
    val scope = CoroutineScope(coroutineContext + deferred)
    val continuation = Continuation(scope.coroutineContext, deferred::complete)
    block.startCoroutine(receiver = scope, completion = continuation)
    return deferred.await()
}

/**
 * Get the current coroutine context.
 *
 * This function is fully equivalent to the [coroutineContext] value. This
 * function is useful in the following case: when a [CoroutineScope] is in
 * scope as `this`, bare `coroutineContext` mention gets resolved as
 * [CoroutineScope.coroutineContext], not [kotlin.coroutines.coroutineContext]:
 *
 * ```
 * coroutineScope {
 *     launch {
 *         // Resolved to CoroutineScope.coroutineContext,
 *         // not kotlin.coroutines.coroutineContext
 *         val context = coroutineContext
 *         val actualContext = currentCoroutineContext()
 *     }
 * }
 * ```
 *
 * This function helps you unambiguously refer to the current coroutine
 * context, not that of the scope. Alternatively to using this function, you
 * can refer to [kotlin.coroutines.coroutineContext] by its fully-qualified
 * name, or add an import alias for it.
 */
suspend fun currentCoroutineContext(): CoroutineContext = coroutineContext