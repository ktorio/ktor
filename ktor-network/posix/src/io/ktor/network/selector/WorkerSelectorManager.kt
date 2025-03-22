/*
 * Copyright 2014-2024 JetBrains s.r.o and contributors. Use of this source code is governed by the Apache 2.0 license.
 */

package io.ktor.network.selector

import kotlinx.coroutines.*
import kotlinx.io.*
import kotlin.coroutines.*

internal class WorkerSelectorManager(context: CoroutineContext) : SelectorManager {
    override val coroutineContext: CoroutineContext = context + CoroutineName("selector")

    private val selector = SelectorHelper()

    init {
        selector.start(this)
    }

    override fun notifyClosed(selectable: Selectable) {
        selector.notifyClosed(selectable.descriptor)
    }

    override suspend fun select(
        selectable: Selectable,
        interest: SelectInterest
    ) {
        return suspendCancellableCoroutine { continuation ->
            val selectorState = EventInfo(selectable.descriptor, interest, continuation)
            if (!selector.interest(selectorState)) {
                continuation.resumeWithException(IOException("Selector closed."))
            }
        }
    }

    override fun close() {
        selector.requestTermination()
    }
}
