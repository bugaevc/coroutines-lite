package io.github.bugaevc.coroutineslite

import io.github.bugaevc.coroutineslite.test.runBlockingTest
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import java.util.concurrent.atomic.AtomicBoolean
import kotlin.coroutines.cancellation.CancellationException

internal class JobTest {
    @Test
    fun `explicit job`() {
        val job = Job()
        assertTrue(job.isActive)
        assertFalse(job.isCompleted)
        assertFalse(job.isCancelled)

        runBlockingTest {
            launch {
                delay(10)
                job.complete()
                assertFalse(job.isActive)
                assertTrue(job.isCompleted)
                assertFalse(job.isCancelled)
            }
            job.join()
        }
    }

    @Test
    fun `cancellation throws exception`() {
        val b = AtomicBoolean(false)
        val cause = CancellationException("Changed my mind")
        runBlockingTest {
            launch {
                val thrown = assertThrows<CancellationException> {
                    delay(100)
                }
                assertSame(cause, thrown)
                b.set(true)
            }.cancelAndJoin(cause)
            assertTrue(b.get())
        }
    }

    @Test
    fun `exception propagation`() {
        val parent = Job()
        val child = Job(parent)
        val sibling = Job(parent)

        var thrown: Throwable? = null
        parent.invokeOnCancellation { th ->
            thrown = th
        }

        val cause = RuntimeException("Whoopsie!")
        child.completeExceptionally(cause)

        assertFalse(child.isActive)
        assertTrue(child.isCancelled)
        assertTrue(child.isCompleted)

        assertFalse(sibling.isActive)
        assertTrue(sibling.isCancelled)
        assertFalse(sibling.isCompleted)

        assertFalse(parent.isActive)
        assertTrue(parent.isCancelled)
        assertFalse(parent.isCompleted)

        assertSame(cause, thrown)
    }
}