package io.github.bugaevc.coroutineslite.channels

enum class BufferOverflow {
    SUSPEND,
    DROP_OLDEST,
    DROP_LATEST,
}