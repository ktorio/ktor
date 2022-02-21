/*
* Copyright 2014-2021 JetBrains s.r.o and contributors. Use of this source code is governed by the Apache 2.0 license.
*/

package io.ktor.server.plugins.callloging

import io.ktor.events.*
import io.ktor.server.application.*
import org.slf4j.event.*

/**
 * Logs application lifecycle and call events.
 */
public val CallLogging: ApplicationPlugin<CallLoggingConfig> = createApplicationPlugin(
    "CallLogging",
    ::CallLoggingConfig
) {
    val log = pluginConfig.logger ?: application.log
    val filters = pluginConfig.filters
    val formatCall = pluginConfig.formatCall

    fun log(message: String) = when (pluginConfig.level) {
        Level.ERROR -> log.error(message)
        Level.WARN -> log.warn(message)
        Level.INFO -> log.info(message)
        Level.DEBUG -> log.debug(message)
        Level.TRACE -> log.trace(message)
    }

    fun logSuccess(call: ApplicationCall) {
        if (filters.isEmpty() || filters.any { it(call) }) {
            log(formatCall(call))
        }
    }

    setupMDCProvider()
    setupLogging(application.environment.monitor, ::log)

    if (pluginConfig.mdcEntries.isEmpty()) {
        logCompletedCalls(::logSuccess)
        return@createApplicationPlugin
    }

    logCallsWithMDC(::logSuccess)
}

private fun PluginBuilder<CallLoggingConfig>.logCompletedCalls(logSuccess: (ApplicationCall) -> Unit) {
    onCall { call ->
        call.afterFinish {
            if (it == null) return@afterFinish
            logSuccess(call)
        }
    }
}

private fun PluginBuilder<CallLoggingConfig>.logCallsWithMDC(logSuccess: (ApplicationCall) -> Unit) {
    val entries = pluginConfig.mdcEntries

    on(MDCHook(ApplicationCallPipeline.Monitoring)) { call, block ->
        withMDC(entries, call, block)
    }

    on(MDCHook(ApplicationCallPipeline.Call)) { call, block ->
        withMDC(entries, call, block)
    }

    on(MDCHook(ApplicationCallPipeline.Fallback)) { call, block ->
        withMDC(entries, call) {
            block()
            logSuccess(call)
        }
    }
}

private fun setupLogging(events: Events, log: (String) -> Unit) {
    val starting: (Application) -> Unit = { log("Application starting: $it") }
    val started: (Application) -> Unit = { log("Application started: $it") }
    val stopping: (Application) -> Unit = { log("Application stopping: $it") }
    var stopped: (Application) -> Unit = {}

    stopped = {
        log("Application stopped: $it")
        events.unsubscribe(ApplicationStarting, starting)
        events.unsubscribe(ApplicationStarted, started)
        events.unsubscribe(ApplicationStopping, stopping)
        events.unsubscribe(ApplicationStopped, stopped)
    }

    events.subscribe(ApplicationStarting, starting)
    events.subscribe(ApplicationStarted, started)
    events.subscribe(ApplicationStopping, stopping)
    events.subscribe(ApplicationStopped, stopped)
}
