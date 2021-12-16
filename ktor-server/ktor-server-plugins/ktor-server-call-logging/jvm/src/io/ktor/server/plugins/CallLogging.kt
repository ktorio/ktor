/*
* Copyright 2014-2021 JetBrains s.r.o and contributors. Use of this source code is governed by the Apache 2.0 license.
*/

package io.ktor.server.plugins

import io.ktor.events.*
import io.ktor.http.*
import io.ktor.server.application.*
import io.ktor.server.logging.*
import io.ktor.server.request.*
import io.ktor.util.*
import io.ktor.util.pipeline.*
import kotlinx.coroutines.*
import org.fusesource.jansi.*
import org.slf4j.*
import org.slf4j.event.*
import kotlin.coroutines.*

/**
 * Logs application lifecycle and call events.
 */
public class CallLogging private constructor(
    private val log: Logger,
    private val monitor: Events,
    private val level: Level,
    private val filters: List<(ApplicationCall) -> Boolean>,
    private val mdcEntries: List<MDCEntry>,
    private val formatCall: (ApplicationCall) -> String
) : MDCProvider {

    internal class MDCEntry(val name: String, val provider: (ApplicationCall) -> String?)

    /**
     * Configuration for [CallLogging] plugin
     */
    public class Configuration {
        internal val filters = mutableListOf<(ApplicationCall) -> Boolean>()
        internal val mdcEntries = mutableListOf<MDCEntry>()
        internal var formatCall: (ApplicationCall) -> String = ::defaultFormat
        internal var isColorsEnabled: Boolean = true

        /**
         * Logging level for [CallLogging], default is [Level.INFO]
         */
        public var level: Level = Level.INFO

        /**
         * Customize [Logger], will default to [ApplicationEnvironment.log]
         */
        public var logger: Logger? = null

        /**
         * Log messages for calls matching a [predicate]
         */
        public fun filter(predicate: (ApplicationCall) -> Boolean) {
            filters.add(predicate)
        }

        /**
         * Put a diagnostic context value to [MDC] with the specified [name] and computed using [provider] function.
         * A value will be available in MDC only during [ApplicationCall] lifetime and will be removed after call
         * processing.
         */
        public fun mdc(name: String, provider: (ApplicationCall) -> String?) {
            mdcEntries.add(MDCEntry(name, provider))
        }

        /**
         * Configure application call log message.
         */
        public fun format(formatter: (ApplicationCall) -> String) {
            formatCall = formatter
        }

        /**
         * Disables colors in log message in case the default formatter was used.
         * */
        public fun disableDefaultColors() {
            isColorsEnabled = false
        }

        private fun defaultFormat(call: ApplicationCall): String =
            when (val status = call.response.status() ?: "Unhandled") {
                HttpStatusCode.Found -> "${colored(status as HttpStatusCode)}: " +
                    "${call.request.toLogStringWithColors()} -> ${call.response.headers[HttpHeaders.Location]}"
                "Unhandled" -> "${colored(status, Ansi.Color.RED)}: ${call.request.toLogStringWithColors()}"
                else -> "${colored(status as HttpStatusCode)}: ${call.request.toLogStringWithColors()}"
            }

        internal fun ApplicationRequest.toLogStringWithColors(): String =
            "${colored(httpMethod.value, Ansi.Color.CYAN)} - ${path()}"

        private fun colored(status: HttpStatusCode): String {
            try {
                if (!AnsiConsole.isInstalled()) {
                    AnsiConsole.systemInstall()
                }
            } catch (cause: Throwable) {
                isColorsEnabled = false // ignore colors if console was not installed
            }

            return when (status) {
                HttpStatusCode.Found, HttpStatusCode.OK, HttpStatusCode.Accepted, HttpStatusCode.Created -> colored(
                    status,
                    Ansi.Color.GREEN
                )
                HttpStatusCode.Continue, HttpStatusCode.Processing, HttpStatusCode.PartialContent,
                HttpStatusCode.NotModified, HttpStatusCode.UseProxy, HttpStatusCode.UpgradeRequired,
                HttpStatusCode.NoContent -> colored(
                    status,
                    Ansi.Color.YELLOW
                )
                else -> colored(status, Ansi.Color.RED)
            }
        }

        private fun colored(value: Any, color: Ansi.Color): String =
            if (isColorsEnabled) Ansi.ansi().fg(color).a(value).reset().toString()
            else value.toString() // ignore color
    }

    private val starting: (Application) -> Unit = { log("Application starting: $it") }
    private val started: (Application) -> Unit = { log("Application started: $it") }
    private val stopping: (Application) -> Unit = { log("Application stopping: $it") }
    private var stopped: (Application) -> Unit = {}

    init {
        stopped = {
            log("Application stopped: $it")
            monitor.unsubscribe(ApplicationStarting, starting)
            monitor.unsubscribe(ApplicationStarted, started)
            monitor.unsubscribe(ApplicationStopping, stopping)
            monitor.unsubscribe(ApplicationStopped, stopped)
        }

        monitor.subscribe(ApplicationStarting, starting)
        monitor.subscribe(ApplicationStarted, started)
        monitor.subscribe(ApplicationStopping, stopping)
        monitor.subscribe(ApplicationStopped, stopped)
    }

    internal fun setupMdc(call: ApplicationCall): Map<String, String> {
        val result = HashMap<String, String>()

        mdcEntries.forEach { entry ->
            entry.provider(call)?.let { mdcValue ->
                result[entry.name] = mdcValue
            }
        }

        return result
    }

    internal fun cleanupMdc() {
        mdcEntries.forEach {
            MDC.remove(it.name)
        }
    }

    /**
     * Installable plugin for [CallLogging].
     */
    public companion object Plugin : ApplicationPlugin<Application, Configuration, CallLogging> {
        override val key: AttributeKey<CallLogging> = AttributeKey("Call Logging")
        override fun install(pipeline: Application, configure: Configuration.() -> Unit): CallLogging {
            val loggingMonitoringPhase = PipelinePhase("LoggingMonitoringCall")
            val loggingBeforeCallPhase = PipelinePhase("LoggingBeforeCall")
            val loggingAfterCallPhase = PipelinePhase("LoggingAfterCall")
            val configuration = Configuration().apply(configure)
            val plugin = CallLogging(
                configuration.logger ?: pipeline.log,
                pipeline.environment.monitor,
                configuration.level,
                configuration.filters.toList(),
                configuration.mdcEntries.toList(),
                configuration.formatCall
            )

            pipeline.insertPhaseBefore(ApplicationCallPipeline.Monitoring, loggingMonitoringPhase)
            pipeline.insertPhaseBefore(ApplicationCallPipeline.Call, loggingBeforeCallPhase)
            pipeline.insertPhaseAfter(ApplicationCallPipeline.Fallback, loggingAfterCallPhase)

            if (plugin.mdcEntries.isNotEmpty()) {
                pipeline.intercept(loggingMonitoringPhase) {
                    plugin.withMDC(call) {
                        proceed()
                    }
                }
                pipeline.intercept(loggingBeforeCallPhase) {
                    plugin.withMDC(call) {
                        proceed()
                    }
                }
                pipeline.intercept(loggingAfterCallPhase) {
                    plugin.withMDC(call) {
                        proceed()
                        plugin.logSuccess(call)
                    }
                }
            } else {
                pipeline.intercept(loggingMonitoringPhase) {
                    proceed()
                    plugin.logSuccess(call)
                }
            }

            return plugin
        }
    }

    public override suspend fun withMDCBlock(call: ApplicationCall, block: suspend () -> Unit) {
        withMDC(call, block)
    }

    /**
     * Invoke suspend [block] with a context having MDC configured.
     */
    private suspend inline fun withMDC(call: ApplicationCall, crossinline block: suspend () -> Unit) {
        withContext(MDCSurvivalElement(setupMdc(call))) {
            try {
                block()
            } finally {
                cleanupMdc()
            }
        }
    }

    private fun log(message: String) = when (level) {
        Level.ERROR -> log.error(message)
        Level.WARN -> log.warn(message)
        Level.INFO -> log.info(message)
        Level.DEBUG -> log.debug(message)
        Level.TRACE -> log.trace(message)
    }

    private fun logSuccess(call: ApplicationCall) {
        if (filters.isEmpty() || filters.any { it(call) }) {
            log(formatCall(call))
        }
    }
}

private class MDCSurvivalElement(mdc: Map<String, String>) : ThreadContextElement<Map<String, String>> {
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
