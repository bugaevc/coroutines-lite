package io.github.bugaevc.coroutineslite

import org.junit.jupiter.api.Assertions
import kotlin.coroutines.ContinuationInterceptor
import kotlin.coroutines.coroutineContext

internal suspend fun assertOnDispatcher(dispatcher: CoroutineDispatcher) {
    val interceptor = coroutineContext[ContinuationInterceptor]
    Assertions.assertSame(dispatcher, interceptor)
}

internal suspend fun currentJob(): Job? = coroutineContext[Job]