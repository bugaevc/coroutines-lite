package io.github.bugaevc.coroutineslite

import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test
import java.util.concurrent.atomic.AtomicInteger

internal class DispatchersTest {
    @Test
    fun `block default`() {
        val counter = AtomicInteger(0)
        runBlocking {
            for (i in 0 until 100) {
                launch {
                    @Suppress("BlockingMethodInNonBlockingContext")
                    Thread.sleep(100)
                    counter.incrementAndGet()
                }
            }
            delay(50)
            assertNotEquals(0, counter.get())
        }
    }

    @Test
    fun `block io`() {
        val counter = AtomicInteger(0)
        runBlocking {
            for (i in 0 until 100) {
                launch(Dispatchers.IO) {
                    @Suppress("BlockingMethodInNonBlockingContext")
                    Thread.sleep(100)
                    counter.incrementAndGet()
                }
            }
            delay(50)
            assertEquals(0, counter.get())
            delay(100)
            assertEquals(100, counter.get())
        }
    }

    @Test
    fun `unconfined works`() {
        val initialThread = Thread.currentThread()
        runBlocking(Dispatchers.Unconfined) {
            assertSame(initialThread, Thread.currentThread())
            delay(1)
            assertSame(initialThread, Thread.currentThread())
        }
    }
}