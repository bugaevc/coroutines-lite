package io.github.bugaevc.coroutineslite

import io.github.bugaevc.coroutineslite.test.runBlockingTest
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import java.util.concurrent.atomic.AtomicInteger
import kotlin.random.Random

internal class CoroutineScopeTest {
    @Test
    fun `delay in parallel`() {
        val counter = AtomicInteger(0)
        runBlockingTest {
            for (i in 0 until 100) {
                launch {
                    delay(10)
                    counter.incrementAndGet()
                }
            }
            delay(20)
            assertEquals(100, counter.get())
        }
    }

    @Test
    fun `parallel decomposition`() {
        runBlockingTest {
            val ds = mutableListOf<Deferred<Int>>()
            for (i in 0 until 100) {
                val deferred: Deferred<Int> = async {
                    delay(Random.nextLong(100))
                    i
                }
                ds.add(deferred)
            }
            for ((i, deferred) in ds.withIndex()) {
                val v = deferred.await()
                assertEquals(i, v)
            }
        }
    }

    @Test
    fun `wait for children`() {
        val counter = AtomicInteger(0)
        runBlockingTest {
            coroutineScope {
                for (i in 0 until 100) {
                    launch {
                        delay(Random.nextLong(100))
                        counter.incrementAndGet()
                    }
                }
            }
            assertEquals(100, counter.get())
        }
    }

    @Test
    fun `wait for cancelled children`() {
        val counter = AtomicInteger(0)
        runBlockingTest {
            coroutineScope {
                for (i in 0 until 100) {
                    launch {
                        delay(Random.nextLong(100))
                        counter.incrementAndGet()
                    }
                }
            }
            assertEquals(100, counter.get())
        }
    }
}