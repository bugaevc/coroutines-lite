package io.github.bugaevc.coroutineslite

import io.github.bugaevc.coroutineslite.test.runBlockingTest
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

internal class Basic {
    @Test
    fun `hello world`() {
        val greeting = runBlockingTest {
            "hello world"
        }
        assertEquals("hello world", greeting)
    }

    @Test
    fun `simple delay`() {
        runBlockingTest {
            delay(100)
        }
    }
}