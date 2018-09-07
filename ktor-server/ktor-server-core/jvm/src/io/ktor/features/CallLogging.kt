package io.ktor.features

import io.ktor.application.*
import io.ktor.http.*
import io.ktor.util.pipeline.*
import io.ktor.request.*
import io.ktor.util.*
import kotlinx.coroutines.*
import org.slf4j.*
import org.slf4j.event.*
import kotlin.coroutines.*

/**
 * Logs application lifecycle and call events.
 */
class CallLogging private constructor(
    private val log: Logger,
    private val monitor: ApplicationEvents,
    private val level: Level,
    private val filters: List<(ApplicationCall) -> Boolean>,
    private val mdcEntries: List<MDCEntry>
) {

    internal class MDCEntry(val name: String, val provider: (ApplicationCall) -> String?)

    /**
     * Configuration for [CallLogging] feature
     */
    class Configuration {
        internal val filters = mutableListOf<(ApplicationCall) -> Boolean>()
        internal val mdcEntries = mutableListOf<MDCEntry>()

        /**
         * Logging level for [CallLogging], default is [Level.TRACE]
         */
        var level: Level = Level.TRACE

        /**
         * Log messages for calls matching a [predicate]
         */
        fun filter(predicate: (ApplicationCall) -> Boolean) {
            filters.add(predicate)
        }

        /**
         * Put a diagnostic context value to [MDC] with the specified [name] and computed using [provider] function.
         * A value will be available in MDC only during [ApplicationCall] lifetime and will be removed after call
         * processing.
         */
        fun mdc(name: String, provider: (ApplicationCall) -> String?) {
            mdcEntries.add(MDCEntry(name, provider))
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

    private fun setupMdc(call: ApplicationCall): Map<String, String> {
        val result = HashMap<String, String>()

        mdcEntries.forEach { entry ->
            entry.provider(call)?.let { mdcValue ->
                result[entry.name] = mdcValue
            }
        }

        return result
    }

    private fun cleanupMdc() {
        mdcEntries.forEach {
            MDC.remove(it.name)
        }
    }

    /**
     * Installable feature for [CallLogging].
     */
    companion object Feature : ApplicationFeature<Application, Configuration, CallLogging> {
        override val key: AttributeKey<CallLogging> = AttributeKey("Call Logging")
        override fun install(pipeline: Application, configure: Configuration.() -> Unit): CallLogging {
            val loggingPhase = PipelinePhase("Logging")
            val configuration = Configuration().apply(configure)
            val feature = CallLogging(
                pipeline.log, pipeline.environment.monitor,
                configuration.level,
                configuration.filters.toList(),
                configuration.mdcEntries.toList()
            )

            pipeline.insertPhaseBefore(ApplicationCallPipeline.Monitoring, loggingPhase)

            if (feature.mdcEntries.isNotEmpty()) {
                pipeline.intercept(loggingPhase) {
                    val mdc = feature.setupMdc(call)
                    withContext(MDCSurvivalElement(mdc)) {
                        try {
                            proceed()
                            feature.logSuccess(call)
                        } finally {
                            feature.cleanupMdc()
                        }
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

    private fun log(message: String) = when (level) {
        Level.ERROR -> log.error(message)
        Level.WARN -> log.warn(message)
        Level.INFO -> log.info(message)
        Level.DEBUG -> log.debug(message)
        Level.TRACE -> log.trace(message)
    }

    private fun logSuccess(call: ApplicationCall) {
        if (filters.isEmpty() || filters.any { it(call) }) {
            val status = call.response.status() ?: "Unhandled"
            when (status) {
                HttpStatusCode.Found -> log("$status: ${call.request.toLogString()} -> ${call.response.headers[HttpHeaders.Location]}")
                else -> log("$status: ${call.request.toLogString()}")
            }
        }
    }
}

/**
 * Generates a string representing this [ApplicationRequest] suitable for logging
 */
fun ApplicationRequest.toLogString(): String = "${httpMethod.value} - ${path()}"

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
