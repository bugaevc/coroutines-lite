package io.github.bugaevc.coroutineslite.channels

import io.github.bugaevc.coroutineslite.delay
import io.github.bugaevc.coroutineslite.test.runBlockingTest
import io.github.bugaevc.coroutineslite.yield
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import java.util.concurrent.atomic.AtomicBoolean

internal class ChannelTest {
    @Test
    fun basic() {
        val channel = Channel<Int>(capacity = 3)

        channel.trySend(1).getOrThrow()
        channel.trySend(2).getOrThrow()
        channel.trySend(3).getOrThrow()

        assertTrue(channel.trySend(4).isFailure)
        assertEquals(1, channel.tryReceive().getOrThrow())
        channel.trySend(4).getOrThrow()
        assertFalse(channel.isEmpty)

        assertEquals(2, channel.tryReceive().getOrThrow())
        assertEquals(3, channel.tryReceive().getOrThrow())
        assertEquals(4, channel.tryReceive().getOrThrow())

        assertTrue(channel.isEmpty)
    }

    @Test
    fun `send waits for receive`() {
        val b = AtomicBoolean(false)
        val channel = Channel<Int>()

        runBlockingTest {
            launch {
                delay(10)
                b.set(true)
                assertEquals(42, channel.receive())
            }

            channel.send(42)
            assertTrue(b.get())
        }
    }

    @Test
    fun `receive waits for send`() {
        val b = AtomicBoolean(false)
        val channel = Channel<Int>(capacity = 10)

        runBlockingTest {
            launch {
                delay(10)
                b.set(true)
                channel.send(42)
            }

            assertEquals(42, channel.receive())
            assertTrue(b.get())
        }
    }

    @Test
    fun closing() {
        val channel = Channel<Int>(capacity = 3)
        channel.trySend(1).getOrThrow()
        channel.trySend(2).getOrThrow()
        val cause = RuntimeException("Error")
        channel.close(cause)
        assertTrue(channel.isClosedForSend)
        assertFalse(channel.isClosedForReceive)
        assertTrue(channel.trySend(3).isClosed)
        assertSame(cause, channel.trySend(4).exceptionOrNull())
        assertEquals(1, channel.tryReceive().getOrThrow())
        assertEquals(2, channel.tryReceive().getOrThrow())
        assertTrue(channel.isClosedForReceive)
        assertTrue(channel.tryReceive().isClosed)
    }

    @Test
    fun `closing wakes up receivers`() {
        val channel = Channel<Int>(capacity = 3)
        runBlockingTest {
            val job = launch {
                assertThrows<ClosedReceiveChannelException> {
                    channel.receive()
                }
            }

            yield()
            channel.close()
            job.join()
        }
    }
}