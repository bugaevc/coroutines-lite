package io.github.bugaevc.coroutineslite.channels

sealed class ChannelResult<out E> {
    private data class Success<E>(val element: E) : ChannelResult<E>()
    private open class Failure : ChannelResult<Nothing>()
    private data class Closed(val cause: Throwable?) : Failure()

    val isSuccess: Boolean
        get() = this is Success

    val isFailure: Boolean
        get() = this is Failure

    val isClosed: Boolean
        get() = this is Closed

    fun getOrNull(): E? = when (this) {
        is Success -> this.element
        else -> null
    }

    fun getOrThrow(): E = when {
        this is Success -> this.element
        this is Closed && this.cause != null -> throw cause
        else -> throw RuntimeException("getOrThrow() failed")
    }

    fun exceptionOrNull(): Throwable? = when (this) {
        is Closed -> cause
        else -> null
    }

    private object SharedFailure : Failure()

    companion object {
        fun <E> success(element: E): ChannelResult<E> {
            return Success<E>(element)
        }

        fun <E> failure(): ChannelResult<E> {
            return SharedFailure
        }

        fun <E> closed(cause: Throwable?): ChannelResult<E> {
            return Closed(cause)
        }
    }
}
