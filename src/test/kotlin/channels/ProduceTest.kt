package io.github.bugaevc.coroutineslite.channels

import io.github.bugaevc.coroutineslite.Job
import io.github.bugaevc.coroutineslite.currentCoroutineContext
import io.github.bugaevc.coroutineslite.delay
import io.github.bugaevc.coroutineslite.test.runBlockingTest
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test

internal class ProduceTest {
    @Test
    fun basic() {
        runBlockingTest {
            var produceJob: Job? = null
            val ch: ReceiveChannel<Int> = produce {
                produceJob = currentCoroutineContext()[Job]
                send(1)
                send(2)
                // Don't explicitly close it here.
            }
            assertEquals(1, ch.receive())
            assertEquals(2, ch.receive())
            delay(1)
            assertTrue(ch.isClosedForReceive)
            assertNull(ch.tryReceive().exceptionOrNull())
            assertTrue(produceJob!!.isCompleted)
        }
    }

    @Test
    fun `exception propagation`() {
        runBlockingTest {
            val exception = RuntimeException("Error!")
            var produceJob: Job? = null
            val ch: ReceiveChannel<Int> = produce {
                produceJob = currentCoroutineContext()[Job]
                throw exception
            }
            try {
                delay(1)
            } catch (ex: Throwable) {
                // This is not supposed to happen.
                // Provide a nice error message.
                assertDoesNotThrow {
                    throw ex
                }
            }
            // We should still be alive!
            val ourJob = currentCoroutineContext()[Job]!!
            assertFalse(ourJob.isCancelled)
            assertTrue(produceJob!!.isCancelled)
            assertTrue(ch.isClosedForReceive)
            assertSame(exception, ch.tryReceive().exceptionOrNull())
        }
    }
}