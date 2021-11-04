package io.github.bugaevc.coroutineslite.test

import io.github.bugaevc.coroutineslite.EventLoop
import kotlin.coroutines.Continuation
import kotlin.coroutines.CoroutineContext

class TestCoroutineDispatcher : EventLoop(), DelayController {
    override var currentTime: Long = 0
        private set

    override fun advanceTimeBy(delayTimeMillis: Long): Long {
        val endTime = currentTime + delayTimeMillis
        do {
            lock.lock()
            try {
                currentTime = minOf(endTime, nextWakeupTime())
            } finally {
                lock.unlock()
            }
            runCurrent()
        } while (currentTime <= endTime)
        return currentTime
    }

    override fun advanceUntilIdle(): Long {
        do {
            lock.lock()
            try {
                currentTime = nextWakeupTime()
            } finally {
                lock.unlock()
            }
            runCurrent()
        } while (currentTime != Long.MAX_VALUE)
        return currentTime
    }

    override fun dispatch(context: CoroutineContext, runnable: Runnable) {
        lock.lock()
        try {
            val continuation = Continuation<Unit>(context) {
                runnable.run()
            }
            val delayed = Delayed(
                time = currentTime,
                continuation = continuation,
            )
            delayeds.add(delayed)
        } finally {
            lock.unlock()
        }
    }

    fun <T> runBlockingTest(
        context: CoroutineContext,
        block: suspend () -> T,
    ): T {
        return super.runBlocking(context, block) { wakeupTime ->
            currentTime = wakeupTime
            lock.unlock()
            if (currentTime == Long.MAX_VALUE) {
                throw RuntimeException("Time advanced until Long.MAX_VALUE")
            }
        }
    }
}