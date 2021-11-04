package io.github.bugaevc.coroutineslite.flow

import io.github.bugaevc.coroutineslite.channels.Channel
import io.github.bugaevc.coroutineslite.delay
import io.github.bugaevc.coroutineslite.test.runBlockingTest
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test
import java.util.concurrent.atomic.AtomicBoolean

internal class StateFlowTest {
    @Test
    fun basic() {
        val flow = MutableStateFlow(42)
        assertEquals(42, flow.value)
        flow.value = 35
        assertEquals(35, flow.value)
        val exchanged = flow.compareAndSet(35, 411)
        assertTrue(exchanged)
        assertEquals(411, flow.value)
        val exchanged2 = flow.compareAndSet(42, 1)
        assertFalse(exchanged2)
        assertEquals(411, flow.value)
    }

    @Test
    fun `notifies collectors`() {
        runBlockingTest {
            val flow = MutableStateFlow(0L)
            val ch = Channel<Long>()
            val collectJob = launch {
                flow.filter { it != 0L }.collect { value ->
                    assertEquals(value, flow.value)
                    assertEquals(currentTime, flow.value)
                    ch.send(value)
                }
                throw RuntimeException("Should never get here")
            }
            launch {
                for (i in 0 until 100) {
                    delay(100)
                    assertTrue(ch.tryReceive().isFailure)
                    flow.value = currentTime
                    assertEquals(flow.value, ch.receive())
                }
                collectJob.cancel()
            }
        }
    }

    @Test
    fun `works with null values`() {
        val flow = MutableStateFlow<Int?>(42)
        val b = AtomicBoolean(false)
        runBlockingTest {
            launch {
                flow.takeWhile { value ->
                    when (value) {
                        null -> {
                            b.set(true)
                            false
                        }
                        else -> true
                    }
                }.collect()
            }
            delay(100)
            flow.value = null
        }
        assertTrue(b.get())
    }
}