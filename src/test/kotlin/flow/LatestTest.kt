package io.github.bugaevc.coroutineslite.flow

import io.github.bugaevc.coroutineslite.Job
import io.github.bugaevc.coroutineslite.currentCoroutineContext
import io.github.bugaevc.coroutineslite.delay
import io.github.bugaevc.coroutineslite.test.runBlockingTest
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import kotlin.coroutines.CoroutineContext

internal class LatestTest {
    @Test
    fun `map latest`() {
        val f = flow<Int> {
            emit(1)
            delay(10)
            emit(2)
            emit(3)
            delay(10)
            emit(4)
        }.mapLatest { value ->
            delay(5)
            value + 10
        }

        val items = runBlockingTest { f.toList() }
        assertEquals(listOf(11, 13, 14), items)
    }

    @Test
    fun `map latest preserves context`() {
        runBlockingTest {
            val outerContext = currentCoroutineContext()
            var flowContext: CoroutineContext? = null
            flow<Int> {
                flowContext = currentCoroutineContext()
                assertNotSame(outerContext, flowContext)
                emit(1)
                delay(10)
                emit(2)
                emit(3)
                delay(10)
                emit(4)
            }.mapLatest { value ->
                assertNotSame(outerContext, currentCoroutineContext())
                assertNotSame(flowContext, currentCoroutineContext())
                delay(5)
                value + 10
            }.collect {
                assertSame(outerContext, currentCoroutineContext())
            }
        }
    }

    @Test
    fun `map latest failure in flow`() {
        runBlockingTest {
            val ex = RuntimeException("Error!")
            var flowJob: Job? = null
            var mapJob: Job? = null
            val f = flow<Int> {
                flowJob = currentCoroutineContext()[Job]
                emit(1)
                delay(50)
                throw ex
            }.mapLatest { _ ->
                mapJob = currentCoroutineContext()[Job]
                val thrown = assertThrows<RuntimeException> {
                    delay(100)
                }
                assertSame(ex, thrown)
            }
            val thrown = assertThrows<RuntimeException> {
                f.collect()
            }
            assertSame(ex, thrown)
            assertTrue(flowJob!!.isCancelled)
            assertTrue(mapJob!!.isCancelled)
            assertFalse(currentCoroutineContext()[Job]!!.isCancelled)
        }
    }
}