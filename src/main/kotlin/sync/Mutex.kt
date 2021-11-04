package io.github.bugaevc.coroutineslite.sync

import io.github.bugaevc.coroutineslite.suspendCancellableCoroutine
import kotlin.coroutines.Continuation
import kotlin.coroutines.resume

/**
 * A mutual exclusion primitive.
 *
 * Only one coroutine can hold the mutex at a time.
 */
class Mutex(initiallyLocked: Boolean = false) {
    private val waiters = mutableListOf<Continuation<Boolean>>()

    /**
     * Whether this mutex is currently locked.
     */
    var isLocked: Boolean = initiallyLocked
       private set

    /**
     * Try to lock this mutex, without suspending.
     *
     * @return Whether locking succeeded.
     */
    fun tryLock(): Boolean {
        synchronized(this) {
            if (isLocked) {
                return false
            }
            isLocked = true
            return true
        }
    }

    /**
     * Wait until the mutex is available, then lock it.
     */
    suspend fun lock() {
        do {
            val taken = suspendCancellableCoroutine<Boolean> { cont ->
                synchronized(this) {
                    // Try to take it now.
                    if (tryLock()) {
                        // We locked it, great!
                        cont.resume(true)
                    } else {
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
        } while (!taken)
    }

    /**
     * Unlock the mutex; this will allow someone else to lock it.
     */
    fun unlock() {
        val waiter = synchronized(this) {
            isLocked = false
            waiters.removeFirstOrNull()
        }
        waiter?.resume(false)
    }

    /**
     * Lock this mutex, perform the given [block], then unlock it.
     */
    suspend fun <T> withLock(block: () -> T): T {
        lock()
        try {
            return block()
        } finally {
            unlock()
        }
    }
}