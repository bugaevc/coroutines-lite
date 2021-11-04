package io.github.bugaevc.coroutineslite

import java.util.concurrent.TimeUnit
import java.util.concurrent.locks.ReentrantLock
import kotlin.coroutines.Continuation
import kotlin.coroutines.CoroutineContext
import kotlin.coroutines.resume
import kotlin.coroutines.startCoroutine

abstract class EventLoop : CoroutineDispatcher(), Delay {
    abstract val currentTime: Long

    protected val lock = ReentrantLock()
    protected val condition = lock.newCondition()

    protected data class Delayed(
        val time: Long,
        val continuation: Continuation<Unit>,
    )

    protected val delayeds = mutableListOf<Delayed>()

    fun runCurrent() {
        val timeNow = currentTime
        val currentContinuations = mutableListOf<Continuation<Unit>>()
        lock.lock()
        try {
            val delayedIterator: MutableIterator<Delayed> = delayeds.iterator()
            for (delayed in delayedIterator) {
                if (delayed.time <= timeNow) {
                    delayedIterator.remove()
                    currentContinuations.add(delayed.continuation)
                }
            }
        } finally {
            lock.unlock()
        }
        for (continuation in currentContinuations) {
            continuation.resume(Unit)
        }
    }

    protected fun nextWakeupTime(): Long {
        return delayeds.minOfOrNull { it.time } ?: Long.MAX_VALUE
    }

    override fun scheduleResumeAfterDelay(
        timeMillis: Long,
        continuation: Continuation<Unit>,
    ) {
        if (timeMillis < 0) {
            continuation.resume(Unit)
            return
        }
        lock.lock()
        try {
            val delayed = Delayed(
                time = currentTime + timeMillis,
                continuation = continuation,
            )
            delayeds.add(delayed)
            condition.signal()
        } finally {
            lock.unlock()
        }
    }

    internal fun <T> runBlocking(
        context: CoroutineContext,
        block: suspend () -> T,
        waitUntil: (wakeupTime: Long) -> Unit,
    ): T {
        // We're going to wait for the result. Whatever thread happens to produce
        // the result will store it into the result variable.
        var result: Result<T>? = null
        // The continuation stores the result and notifies the waiting thread:
        val continuation = Continuation<T>(context) { r ->
            lock.lock()
            try {
                result = r
                condition.signal()
            } finally {
                lock.unlock()
            }
        }
        // Start off the block.
        block.startCoroutine(continuation)
        // Now wait for the result.
        while (true) {
            runCurrent()
            lock.lock()
            val wakeupTime: Long
            try {
                wakeupTime = nextWakeupTime()
                // Check if the result is ready.
                val res = result
                if (res != null) {
                    return res.getOrThrow()
                }
            } catch (ex: Throwable) {
                lock.unlock()
                throw ex
            }
            waitUntil(wakeupTime)
        }
    }

    internal fun <T> runBlocking(
        context: CoroutineContext,
        block: suspend () -> T
    ): T = runBlocking(context, block) { wakeupTime: Long ->
        condition.await(wakeupTime - currentTime, TimeUnit.MILLISECONDS)
        lock.unlock()
    }
}