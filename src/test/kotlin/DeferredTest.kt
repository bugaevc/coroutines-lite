package io.github.bugaevc.coroutineslite

import io.github.bugaevc.coroutineslite.test.runBlockingTest
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import java.util.concurrent.atomic.AtomicBoolean
import kotlin.coroutines.cancellation.CancellationException

internal class DeferredTest {
    @Test
    fun `simple await`() {
        runBlockingTest {
            val d = async {
                delay(10)
                42
            }
            assertEquals(42, d.await())
        }
    }

    @Test
    fun `null result`() {
        runBlockingTest {
            val d: Deferred<String?> = async {
                delay(10)
                null
            }
            assertNull(d.await())
        }
    }

    @Test
    fun `instantly ready`() {
        runBlockingTest {
            val d = async {
                35
            }
            assertEquals(35, d.await())
        }
    }

    @Test
    fun `throws exception`() {
        val exception = RuntimeException("Exception!")
        val thrown = assertThrows<RuntimeException> {
            runBlockingTest {
                val d = async {
                    delay(10)
                    throw exception
                }
                val thrown = assertThrows<RuntimeException> { d.await() }
                assertSame(exception, thrown)
            }
        }
        assertSame(exception, thrown)
    }

    @Test
    fun `instantly throws exception`() {
        val exception = RuntimeException("Exception!")
        val thrown = assertThrows<RuntimeException> {
            runBlockingTest {
                val d = async {
                    throw exception
                }
                val thrown = assertThrows<RuntimeException> { d.await() }
                assertSame(exception, thrown)
            }
        }
        assertSame(exception, thrown)
    }

    @Test
    fun cancellation() {
        runBlockingTest {
            val d = async {
                delay(10)
                42
            }
            d.cancel()
            assertThrows<CancellationException> { d.await() }
        }
    }

    @Test
    fun `await all`() {
        val b = AtomicBoolean(false)
        runBlockingTest {
            launch {
                delay(150)
                b.set(true)
            }
            val values = (1..100).map { i ->
                async {
                    delay(100)
                    i + 10
                }
            }.awaitAll()
            assertFalse(b.get())
            assertEquals((11..110).toList(), values)
        }
        assertTrue(b.get())
    }
}