package io.github.bugaevc.coroutineslite.test

interface DelayController {
    val currentTime: Long
    fun advanceTimeBy(delayTimeMillis: Long): Long
    fun advanceUntilIdle(): Long
    fun runCurrent()
}