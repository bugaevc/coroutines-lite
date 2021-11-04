package io.github.bugaevc.coroutineslite

import org.junit.jupiter.api.Assertions.assertSame
import org.junit.jupiter.api.Test
import kotlin.coroutines.CoroutineContext

internal class CoroutineExceptionHandlerTest {
    @Test
    fun `with exception handler`() {
        var c: CoroutineContext? = null
        var ex: Throwable? = null
        val handler = CoroutineExceptionHandler { context, exception ->
            c = context
            ex = exception
        }
        val scope = CoroutineScope(handler)
        val cause = RuntimeException("Error!11")
        val job = scope.launch {
            delay(1)
            throw cause
        }
        runBlocking {
            job.join()
        }
        assertSame(cause, ex)
        assertSame(scope.coroutineContext, c)
    }
}