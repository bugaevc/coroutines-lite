package io.github.bugaevc.coroutineslite

import java.util.concurrent.Executor
import java.util.concurrent.Executors
import java.util.concurrent.ForkJoinPool
import kotlin.coroutines.Continuation
import kotlin.coroutines.CoroutineContext

object Dispatchers {
    // A single work-stealing thread pool shared
    // between Default and IO dispatchers.
    private val pool = Executors.newWorkStealingPool()

    /**
     * The default dispatcher, intended for potentially CPU-heavy tasks.
     *
     * It's fine to run tasks that are not CPU-heavy on this dispatcher; but
     * it's not fine to run tasks that may block a thread for a long time.
     * Use [Dispatchers.IO] for that purpose.
     *
     * This dispatcher is backed by a work-stealing thread pool whose size is
     * determined by the number of CPU cores.
     */
    val Default: CoroutineDispatcher =
        ExecutorDispatcher("Dispatchers.Default", pool, blocking = false)

    /**
     * A dispatcher suitable for blocking I/O.
     *
     * This dispatcher is prepared to accept tasks that are not CPU-heavy, but
     * can potentially block a thread for a long time, such as blocking I/O
     * operations.
     *
     * This dispatcher is backed by a work-stealing thread pool whose size is
     * dynamically adjusted.
     */
    val IO: CoroutineDispatcher =
        ExecutorDispatcher("Dispatchers.IO", pool, blocking = true)

    /**
     * A special dispatcher that always runs continuations in-place.
     */
    val Unconfined: CoroutineDispatcher =
        UnconfinedDispatcher("Dispatchers.Unconfined")
}

private class ExecutorDispatcher(
    private val name: String,
    private val executor: Executor,
    private val blocking: Boolean = false,
) : EventLoop() {

    override val currentTime: Long
        get() = System.currentTimeMillis()

    override fun dispatch(context: CoroutineContext, runnable: Runnable) {
        if (!blocking) {
            executor.execute(runnable)
        } else {
            val blocker = object : ForkJoinPool.ManagedBlocker {
                override fun isReleasable() = false
                override fun block(): Boolean {
                    runnable.run()
                    return true
                }
            }
            executor.execute {
                ForkJoinPool.managedBlock(blocker)
            }
        }
    }

    override fun toString() = name
}

private class UnconfinedDispatcher(
    private val name: String,
) : CoroutineDispatcher() {

    override fun dispatch(context: CoroutineContext, runnable: Runnable) {
        // Run it right here.
        runnable.run()
    }

    // Don't intercept anything.
    override fun <T> interceptContinuation(
        continuation: Continuation<T>,
    ): Continuation<T> = continuation

    override fun toString() = name
}