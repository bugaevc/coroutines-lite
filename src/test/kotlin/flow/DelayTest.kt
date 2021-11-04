package io.github.bugaevc.coroutineslite.flow

import io.github.bugaevc.coroutineslite.delay
import io.github.bugaevc.coroutineslite.test.runBlockingTest
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

internal class DelayTest {
    @Test
    fun `basic debounce`() {
        val f = flow<Int> {
            emit(1)
            emit(2)
            delay(100)
            emit(3)
            emit(4)
            delay(50)
            emit(5)
        }

        val items = runBlockingTest {
            f.debounce(60)
                .map { value -> value to currentTime }
                .toList()
        }
        assertEquals(
            listOf(
                2 to 60L,
                5 to 150L,
            ), items
        )
    }

    @Test
    fun `waiting after last item`() {
        val f = flow<Int> {
            emit(1)
            delay(100)
            emit(2)
            delay(100)
        }
        runBlockingTest {
            val items = f.debounce(50)
                .map { value -> value to currentTime }
                .toList()
            assertEquals(200, currentTime)
            assertEquals(
                listOf(
                    1 to 50L,
                    2 to 150L,
                ), items
            )
        }
    }
}