package io.github.bugaevc.coroutineslite.channels

class ClosedSendChannelException : IllegalStateException(
    "Attempt to send to a closed channel"
)

class ClosedReceiveChannelException : NoSuchElementException()