package io.github.bugaevc.coroutineslite.flow

import io.github.bugaevc.coroutineslite.runBlocking
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

internal class LimitTest {
    @Test
    fun `drop zero`() {
        val f = flowOf(1, 2, 3).drop(0)
        val l = runBlocking { f.toList() }
        assertEquals(listOf(1, 2, 3), l)
    }

    @Test
    fun `drop some`() {
        val f = flowOf(1, 2, 3, 4).drop(2)
        val l = runBlocking { f.toList() }
        assertEquals(listOf(3, 4), l)
    }

    @Test
    fun `drop more than present`() {
        val f = flowOf(1, 2, 3, 4).drop(10)
        val l = runBlocking { f.toList() }
        assertEquals(emptyList<Int>(), l)
    }

    @Test
    fun `drop while even`() {
        val f = flowOf(0, 2, 4, 6, 7, 8, 9, 10)
            .dropWhile { it % 2 == 0 }
        val l = runBlocking { f.toList() }
        assertEquals(listOf(7, 8, 9, 10), l)
    }

    @Test
    fun `take zero`() {
        val f = flowOf(1, 2, 3, 4).take(0)
        val l = runBlocking { f.toList() }
        assertEquals(emptyList<Int>(), l)
    }

    @Test
    fun `take one`() {
        val f = flowOf(1, 2, 3, 4).take(1)
        val l = runBlocking { f.toList() }
        assertEquals(listOf(1), l)
    }

    @Test
    fun `take some`() {
        val f = flowOf(1, 2, 3, 4).take(2)
        val l = runBlocking { f.toList() }
        assertEquals(listOf(1, 2), l)
    }

    @Test
    fun `take while even`() {
        val f = flowOf(0, 2, 4, 6, 7, 8, 9, 10)
            .takeWhile { it % 2 == 0 }
        val l = runBlocking { f.toList() }
        assertEquals(listOf(0, 2, 4, 6), l)
    }
}