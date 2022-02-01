/*
 * Copyright 2014-2022 JetBrains s.r.o and contributors. Use of this source code is governed by the Apache 2.0 license.
 */

package io.ktor.server.plugins.callloging

import io.ktor.server.application.*
import kotlinx.coroutines.*
import org.slf4j.*
import kotlin.coroutines.*

internal class MDCEntry(val name: String, val provider: (ApplicationCall) -> String?)

internal suspend inline fun withMDC(
    mdcEntries: List<MDCEntry>,
    call: ApplicationCall,
    crossinline block: suspend () -> Unit
) {
    withContext(MDCSurvivalElement(mdcEntries.setup(call))) {
        try {
            block()
        } finally {
            mdcEntries.cleanup()
        }
    }
}

internal fun List<MDCEntry>.setup(call: ApplicationCall): Map<String, String> {
    val result = HashMap<String, String>()

    forEach { entry ->
        entry.provider(call)?.let { mdcValue ->
            result[entry.name] = mdcValue
        }
    }

    return result
}

internal fun List<MDCEntry>.cleanup() {
    forEach {
        MDC.remove(it.name)
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
