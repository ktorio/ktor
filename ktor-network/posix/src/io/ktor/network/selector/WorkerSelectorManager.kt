/*
 * Copyright 2014-2024 JetBrains s.r.o and contributors. Use of this source code is governed by the Apache 2.0 license.
 */

package io.ktor.network.selector

import kotlinx.coroutines.CoroutineName
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.io.IOException
import kotlin.coroutines.CoroutineContext
import kotlin.coroutines.resumeWithException

internal class WorkerSelectorManager(context: CoroutineContext) : SelectorManager {
    override val coroutineContext: CoroutineContext = context + CoroutineName("selector")

    private val selector = SelectorHelper()

    init {
        selector.start(this)
    }

    override fun notifyClosed(selectable: Selectable) {
        selector.notifyClosed(selectable.descriptor)
    }

    override suspend fun select(selectable: Selectable, interest: SelectInterest) {
        suspendCancellableCoroutine<Unit> { continuation ->
            val event = EventInfo(
                selectable.descriptor,
                interest,
                continuation
            )

            if (selector.interest(event)) {
                continuation.invokeOnCancellation {
                    // Requeue the same cancelled event to wake the selector.
                    // Its loop will discard it rather than register it again.
                    selector.interest(event)
                }
            } else {
                continuation.resumeWithException(IOException("Selector closed."))
            }
        }
    }

    override fun close() {
        selector.requestTermination()
    }
}
