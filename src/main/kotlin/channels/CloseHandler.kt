package io.github.bugaevc.coroutineslite.channels

fun interface CloseHandler {
    fun handleClose(cause: Throwable?)
}