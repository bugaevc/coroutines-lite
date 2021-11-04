package io.github.bugaevc.coroutineslite.sync

import io.github.bugaevc.coroutineslite.suspendCancellableCoroutine
import kotlin.coroutines.Continuation
import kotlin.coroutines.resume

class Semaphore(
    private var permits: Int,
) {
    val availablePermits: Int
        get() = synchronized(this) {
            permits
        }

    private val waiters = mutableListOf<Continuation<Boolean>>()

    fun tryAcquire(): Boolean {
        synchronized(this) {
            if (permits == 0) {
                return false
            }
            permits--
            return true
        }
    }

    suspend fun acquire() {
        do {
            val acquired = suspendCancellableCoroutine<Boolean> { cont ->
                synchronized(this) {
                    // Try to acquire it now.
                    when (tryAcquire()) {
                        true -> cont.resume(true)
                        false -> {
                            // We'll have to wait.
                            waiters.add(cont)
                            cont.invokeOnCancellation {
                                synchronized(this) {
                                    waiters.remove(cont)
                                }
                            }
                        }
                    }
                }
            }
        } while (!acquired)
    }

    fun release() {
        val waiter = synchronized(this) {
            permits++
            waiters.removeFirstOrNull()
        }
        waiter?.resume(false)
    }

    suspend fun <T> withPermit(block: () -> T): T {
        acquire()
        try {
            return block()
        } finally {
            release()
        }
    }
}