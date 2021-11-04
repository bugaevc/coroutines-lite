package io.github.bugaevc.coroutineslite.flow

import io.github.bugaevc.coroutineslite.*
import io.github.bugaevc.coroutineslite.channels.SendChannel
import io.github.bugaevc.coroutineslite.test.runBlockingTest
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import java.util.concurrent.atomic.AtomicBoolean

internal class ChannelFlowTest {
    @Test
    fun `flow on`() {
        val f = flow<Int> {
            assertOnDispatcher(Dispatchers.IO)
            emit(42)
        }.flowOn(Dispatchers.IO)

        runBlocking {
            val outerContext = currentCoroutineContext()
            f.collect { value ->
                assertEquals(42, value)
                assertSame(outerContext, currentCoroutineContext())
            }
        }
    }

    @Test
    fun `conflate never blocks emitter`() {
        val b = AtomicBoolean(false)
        val f = flow<Int> {
            for (i in 0 until 1024) {
                emit(42)
                assertFalse(b.get())
            }
        }.conflate()

        runBlockingTest {
            val outerContext = currentCoroutineContext()
            launch {
                delay(100)
                b.set(true)
            }
            f.collect { value ->
                assertEquals(42, value)
                assertSame(outerContext, currentCoroutineContext())
                delay(200)
            }
            assertTrue(b.get())
        }
    }

    @Test
    fun `close error gets propagated`() {
        val error = RuntimeException("Error!")
        val f = channelFlow<String> {
            send("hello")
            throw error
        }
        runBlockingTest {
            val thrown = assertThrows<RuntimeException> {
                f.collect { value ->
                    assertEquals("hello", value)
                }
            }
            assertSame(error, thrown)
        }
    }

    @Test
    fun `child close error gets propagated`() {
        val error = RuntimeException("Error!")
        val f = channelFlow<String> {
            send("hello")
            launch {
                delay(10)
                throw error
            }
        }
        runBlockingTest {
            val thrown = assertThrows<RuntimeException> {
                f.collect { value ->
                    assertEquals("hello", value)
                }
            }
            assertSame(error, thrown)
        }
    }

    @Test
    fun `multiple flow on fuse top to bottom`() {
        val f = flow<Int> {
            assertOnDispatcher(Dispatchers.IO)
            emit(42)
        }
            .flowOn(Dispatchers.IO)
            .flowOn(Dispatchers.Default)

        runBlocking {
            f.collect()
        }
    }

    @Test
    fun `produce in`() {
        runBlocking {
            val channel = flow<Int> {
                assertOnDispatcher(Dispatchers.IO)
                emit(42)
            }.flowOn(Dispatchers.IO).produceIn(this)
            assertEquals(42, channel.receive())
            assertTrue(channel.receiveCatching().isClosed)
        }
    }

    @Test
    fun `fusing creates a single channel`() {
        var sendChannel: SendChannel<Int>? = null
        val f: Flow<Int> = channelFlow<Int> {
            sendChannel = channel
            send(42)
        }.conflate().buffer(42).flowOn(Dispatchers.IO)
        runBlocking {
            val recvChannel = f.produceIn(this)
            assertEquals(42, recvChannel.receive())
            assertSame(recvChannel, sendChannel)
        }
    }
}