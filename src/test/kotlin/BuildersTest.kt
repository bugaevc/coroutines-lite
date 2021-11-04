package io.github.bugaevc.coroutineslite

import io.github.bugaevc.coroutineslite.test.runBlockingTest
import org.junit.jupiter.api.Assertions
import org.junit.jupiter.api.Assertions.assertSame
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import java.util.concurrent.atomic.AtomicBoolean
import kotlin.coroutines.cancellation.CancellationException

internal class BuildersTest {
    @Test
    fun `changing dispatcher`() {
        runBlocking {
            assertOnDispatcher(Dispatchers.Default)
            val job = currentJob()!!
            withContext(Dispatchers.IO) {
                assertOnDispatcher(Dispatchers.IO)
                assertSame(job, currentJob())
            }
            assertOnDispatcher(Dispatchers.Default)
        }
    }

    @Test
    fun `no cancel parent on failure`() {
        runBlockingTest {
            val exception = RuntimeException("Ooops")
            val thrown = assertThrows<RuntimeException> {
                coroutineScope {
                    throw exception
                }
            }
            assertSame(exception, thrown)
            val job = currentJob()!!
            assertTrue(job.isActive)
        }
    }

    @Test
    fun `outer does not throw separately`() {
        val b = AtomicBoolean(false)
        runBlocking {
            // Note: intentionally not using the launch(Dispatchers.IO) form.
            val job = launch {
                withContext(Dispatchers.IO) {
                    assertThrows<CancellationException> {
                        delay(10)
                    }
                }
                b.set(true)
            }
            delay(5)
            job.cancel()
        }
        assertTrue(b.get())
    }
}