package io.github.bugaevc.coroutineslite

import io.github.bugaevc.coroutineslite.test.runBlockingTest
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows

internal class TimeoutTest {
    @Test
    fun `finishes in time`() {
        runBlockingTest {
            val greeting: String = withTimeout(100) {
                delay(50)
                "hello"
            }
            assertEquals("hello", greeting)
            assertEquals(50, currentTime)
        }
    }

    @Test
    fun `finishes instantly`() {
        runBlockingTest {
            val greeting: String = withTimeout(100) {
                "hello"
            }
            assertEquals("hello", greeting)
            assertEquals(0, currentTime)
        }
    }

    @Test
    fun `times out`() {
        runBlockingTest {
            assertThrows<TimeoutCancellationException> {
                withTimeout(100) {
                    delay(200)
                }
            }
            assertEquals(100, currentTime)
        }
    }

    @Test
    fun `or null`() {
        runBlockingTest {
            val v: String? = withTimeoutOrNull(100) {
                delay(200)
                "hello"
            }
            assertNull(v)
            assertEquals(100, currentTime)
        }
    }

    @Test
    fun `nested or null`() {
        runBlockingTest {
            val v = withTimeoutOrNull(100) {
                assertThrows<TimeoutCancellationException> {
                    withTimeoutOrNull(200) {
                        delay(300)
                    }
                }
            }
            assertNull(v)
            assertEquals(100, currentTime)
        }
    }
}