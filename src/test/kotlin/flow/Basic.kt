package io.github.bugaevc.coroutineslite.flow

import io.github.bugaevc.coroutineslite.currentCoroutineContext
import io.github.bugaevc.coroutineslite.delay
import io.github.bugaevc.coroutineslite.test.runBlockingTest
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertSame
import org.junit.jupiter.api.Test

internal class Basic {
    @Test
    fun `hello world`() {
        val f = flow<String> {
            emit("hello")
            delay(10)
            emit("world")
        }

        val items = runBlockingTest { f.toList() }
        assertEquals(listOf("hello", "world"), items)
    }

    @Test
    fun `context preservation`() {
        runBlockingTest {
            val outerContext = currentCoroutineContext()
            flow<Int> {
                assertSame(outerContext, currentCoroutineContext())
                emit(42)
            }.collect { value ->
                assertSame(outerContext, currentCoroutineContext())
                assertEquals(42, value)
            }
        }
    }
}