/*
* Copyright 2014-2021 JetBrains s.r.o and contributors. Use of this source code is governed by the Apache 2.0 license.
*/

package io.ktor.features

import io.ktor.application.*
import kotlinx.coroutines.*
import org.slf4j.*
import kotlin.coroutines.*

internal actual fun removeFromMDC(key: String) {
    MDC.remove(key)
}
/**
 * Invoke suspend [block] with a context having MDC configured.
 */
internal actual suspend inline fun withMDC(call: ApplicationCall, crossinline block: suspend () -> Unit) {
    val feature = call.application.featureOrNull(CallLogging) ?: return block()

    withContext(MDCSurvivalElement(feature.setupMdc(call))) {
        try {
            block()
        } finally {
            feature.cleanupMdc()
        }
    }
}

internal class MDCSurvivalElement(mdc: Map<String, String>) : ThreadContextElement<Map<String, String>> {
    override val key: CoroutineContext.Key<*> get() = Key

    private val snapshot = copyMDC() + mdc

    override fun restoreThreadContext(context: CoroutineContext, oldState: Map<String, String>) {
        putMDC(oldState)
    }

    override fun updateThreadContext(context: CoroutineContext): Map<String, String> {
        val mdcCopy = copyMDC()
        putMDC(snapshot)
        return mdcCopy
    }

    private fun copyMDC() = MDC.getCopyOfContextMap()?.toMap() ?: emptyMap()

    private fun putMDC(oldState: Map<String, String>) {
        MDC.clear()
        oldState.entries.forEach { (k, v) ->
            MDC.put(k, v)
        }
    }

    private object Key : CoroutineContext.Key<MDCSurvivalElement>
}
