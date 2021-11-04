# Kotlin Coroutines Lite
This is a lightweight coroutines library for Kotlin.

Like the official [kotlinx.coroutines](https://github.com/Kotlin/kotlinx.coroutines), this library implements a coroutines framework on top of the support for `suspend` functions provided by the Kotlin compiler. Coroutines Lite implements an API that is very similar (but not always identical) to _kotlinx.coroutines_.

Unlike _kotlinx.coroutines_, this library is optimized for _clarity_ and _simplicity_, not safety or performance:

* where _kotlinx.coroutines_ uses lock-free linked lists, Coroutines Lite uses `synchronized` with a simple list;
* where _kotlinx.coroutines_ has many optimized fast paths, Coroutines Lite does not;
* where _kotlinx.coroutines_ uses a public interface with multiple specialized private implementations, Coroutines Lite has just a single class;
* where _kotlinx.coroutines_ has invariant-validating wrappers like `SafeCollector`, Coroutines Lite does things directly.

In other words, Coroutines Lite is suitable for experimenting and learning, while _kotlinx.coroutines_ is great for running in production.

## Examples

Spawning a thousand concurrent coroutines, each waiting for 100 ms and returning:

```kotlin
import io.github.bugaevc.coroutineslite.*

runBlocking { 
    for (i in 1..1000) {
        launch {
            delay(timeMillis = 100)
        }
    }
}
println("All done!")
```

Running things concurrently, in a structured manner (parallel decomposition):

```kotlin
import io.github.bugaevc.coroutineslite.*

suspend fun concurrencyDemo(): Pair<Long, String> {
    // Open a coroutine scope. Inside the scope, we can spawn concurrent jobs.
    // The scope will only complete once all the spawned jobs do.
    return coroutineScope {
        // Start the heavy computation on the default dispatcher, which is
        // backed by a work-stealing thread pool whose size is determined
        // by the number of CPU cores.
        val computation: Deferred<Long> = async(Dispatchers.Default) {
            // Do some complex, CPU-heavy computation.
            heavyComputation()
        }
        // Start the blocking I/O on the I/O dispatcher, which is backed
        // by the same thread pool, but informs it that the operations will
        // block, which causes it to spawn more threads to compensate.
        val io: Deferred<String> = async(Dispatchers.IO) {
            // Do some blocking I/O that may block the calling
            // thread for a long time.
            blockingIO()
        }
        // Now wait for both operations to complete. The current thread is
        // not blocked while we're waiting, and it can participate in the
        // thread pool, alongside the other worker threads.
        val computed: Long = computation.await()
        val received: String = io.await()
        // Both operations have completed successfully (otherwise, the calls
        // above would throw an exception), so return the results.
        computed to received
    }
}
```

Fetching a list of things concurrently. Note that if any individual `fetchSomething()` call fails,

1. All the other `fetchSomething()` calls will immediately get cancelled, along with the whole coroutine scope.
2. The scope will wait for all the child jobs to fully complete, i.e. run any post-cancellation cleanup logic.
3. `coroutineScope { }` (and thus `fetchAll()`) will re-throw the original exception.

```kotlin
import io.github.bugaevc.coroutineslite.*

suspend fun fetchSomething(key: Int): String {
    delay(1_000)
    return "value for $key"
}

suspend fun fetchAll(keys: List<Int>): List<String> {
    return coroutineScope { 
        // Map each key to a Deferred<String>,
        // then await them all concurrently.
        keys.map { key -> 
            async {
                fetchSomething(key)
            }
        }.awaitAll()
    }
}
```

Working with an asynchronous flow of values:

```kotlin
import io.github.bugaevc.coroutineslite.*
import io.github.bugaevc.coroutineslite.flow.*

flow<Int> { 
    delay(100)
    emit(35)
    delay(200)
    emit(42)
}.map { value ->
    withContext(Dispatchers.IO) {
        someBlockingRequest(value)
    }
}.collect { value ->
    println("Collected $value")
}
```

Wrapping a callback-based API into a `suspend fun`:

```kotlin
import io.github.bugaevc.coroutineslite.*
import okhttp3.*
import java.io.IOException
import kotlin.coroutines.resumeWithException

/**
 * Await an OkHttp call in a coroutine.
 */
suspend fun Call.await(): Response {
    // Suspend us in a cancellable manner, and get a continuation that
    // we can use to resume ourselves back once the response is ready.
    return suspendCancellableCoroutine { continuation ->
        // Start the call with the following callback:
        enqueue(responseCallback = object : okhttp3.Callback {
            override fun onResponse(call: Call, response: Response) {
                // The call succeeded, and yielded a response. Try to resume
                // the coroutine. If it can't be resumed because it's already
                // cancelled, make sure to close the response.
                continuation.resume(response, onCancellation = {
                    response.close()
                })
            }

            override fun onFailure(call: Call, e: IOException) {
                // The call failed; resume the coroutine with the error.
                continuation.resumeWithException(e)
            }
        })
        // If the coroutine gets cancelled, cancel the call as well.
        continuation.invokeOnCancellation {
            cancel()
        }
    }
}
```

Wrapping a multi-shot callback-based API into a flow using `channelFlow { }`:

```kotlin
import io.github.bugaevc.coroutineslite.*
import io.github.bugaevc.coroutineslite.flow.*

channelFlow<String> { 
    SomeApi.registerCallback(object : SomeApi.Callback {
        override fun onNewItem(item: String) {
            // Send the new item through the channel,
            // which will emit it form the flow. Block this
            // thread in case the flow consumer cannot keep
            // up with new items.
            runBlocking(Dispatchers.Unconfined) {
                send(item)
            }
        }

        override fun onDone() {
            // Close the channel; this indicates the end
            // of the flow.
            close()
        }

        override fun onError(ex: Throwable) {
            // Close the channel with the error; this will
            // cause the error to be propagated to the flow
            // collector.
            close(ex)
        }
    })
    // Don't return from channelFlow { } until explicitly closed.
    awaitClose()
}
```