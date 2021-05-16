/*
* Copyright 2014-2021 JetBrains s.r.o and contributors. Use of this source code is governed by the Apache 2.0 license.
*/

package io.ktor.features

import io.ktor.*
import io.ktor.application.*
import io.ktor.http.*
import io.ktor.request.*
import io.ktor.util.*
import io.ktor.util.pipeline.*
import kotlinx.coroutines.*
import kotlin.coroutines.*

/**
 * Logs application lifecycle and call events.
 */
//TODO better approach for JVM only MDC handling?
public class CallLogging private constructor(
    private val log: Logger,
    private val monitor: ApplicationEvents,
    private val level: LoggingLevel,
    private val filters: List<(ApplicationCall) -> Boolean>,
    private val mdcEntries: List<MDCEntry>,
    private val formatCall: (ApplicationCall) -> String
) {

    internal class MDCEntry(val name: String, val provider: (ApplicationCall) -> String?)

    /**
     * Configuration for [CallLogging] feature
     */
    public class Configuration {
        internal val filters = mutableListOf<(ApplicationCall) -> Boolean>()
        internal val mdcEntries = mutableListOf<MDCEntry>()
        internal var formatCall: (ApplicationCall) -> String = ::defaultFormat

        /**
         * Logging level for [CallLogging], default is [LoggingLevel.TRACE]
         */
        public var level: LoggingLevel = LoggingLevel.TRACE

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
            removeFromMDC(it.name)
        }
    }

    /**
     * Installable feature for [CallLogging].
     */
    public companion object Feature : ApplicationFeature<Application, Configuration, CallLogging> {
        override val key: AttributeKey<CallLogging> = AttributeKey("Call Logging")
        override fun install(pipeline: Application, configure: Configuration.() -> Unit): CallLogging {
            val loggingPhase = PipelinePhase("Logging")
            val configuration = Configuration().apply(configure)
            val feature = CallLogging(
                configuration.logger ?: pipeline.log,
                pipeline.environment.monitor,
                configuration.level,
                configuration.filters.toList(),
                configuration.mdcEntries.toList(),
                configuration.formatCall
            )

            pipeline.insertPhaseBefore(ApplicationCallPipeline.Monitoring, loggingPhase)

            if (feature.mdcEntries.isNotEmpty()) {
                pipeline.intercept(loggingPhase) {
                    withMDC(call) {
                        proceed()
                        feature.logSuccess(call)
                    }
                }
            } else {
                pipeline.intercept(loggingPhase) {
                    proceed()
                    feature.logSuccess(call)
                }
            }

            return feature
        }
    }

    @Suppress("KDocMissingDocumentation")
    @InternalAPI
    public object Internals {
        @InternalAPI
        public suspend fun <C : PipelineContext<*, ApplicationCall>> C.withMDCBlock(block: suspend C.() -> Unit) {
            withMDCBlock(call) { block.invoke(this) }
        }

        @InternalAPI
        public suspend fun withMDCBlock(call: ApplicationCall, block: suspend () -> Unit) {
            withMDC(call, block)
        }
    }

    private fun log(message: String) = when (level) {
        LoggingLevel.ERROR -> log.error(message)
        LoggingLevel.WARN -> log.warn(message)
        LoggingLevel.INFO -> log.info(message)
        LoggingLevel.DEBUG -> log.debug(message)
        LoggingLevel.TRACE -> log.trace(message)
        else -> error("never happen")
    }

    private fun logSuccess(call: ApplicationCall) {
        if (filters.isEmpty() || filters.any { it(call) }) {
            log(formatCall(call))
        }
    }
}

internal expect fun removeFromMDC(key: String)

/**
 * Invoke suspend [block] with a context having MDC configured.
 */
internal expect suspend inline fun withMDC(call: ApplicationCall, crossinline block: suspend () -> Unit)

/**
 * Generates a string representing this [ApplicationRequest] suitable for logging
 */
public fun ApplicationRequest.toLogString(): String = "${httpMethod.value} - ${path()}"

private fun defaultFormat(call: ApplicationCall): String = when (val status = call.response.status() ?: "Unhandled") {
    HttpStatusCode.Found -> "$status: ${call.request.toLogString()} -> ${call.response.headers[HttpHeaders.Location]}"
    else -> "$status: ${call.request.toLogString()}"
}
