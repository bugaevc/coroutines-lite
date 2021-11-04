package io.github.bugaevc.coroutineslite.test

import io.github.bugaevc.coroutineslite.CoroutineScope
import kotlin.coroutines.CoroutineContext

class TestCoroutineScope(
    context: CoroutineContext,
    private val delayController: DelayController,
) : CoroutineScope(context), DelayController by delayController