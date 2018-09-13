package io.ktor.metrics

import io.ktor.application.*
import io.ktor.response.*
import io.ktor.util.*
import io.ktor.util.pipeline.*

class CallMeasurer {
    @PublishedApi
    internal val handlers: ArrayList<PipelineContext<*, ApplicationCall>.(MeasureEntry) -> Unit> = arrayListOf()

    fun addHandler(handler: PipelineContext<*, ApplicationCall>.(MeasureEntry) -> Unit) {
        handlers += handler
    }

    @PublishedApi
    internal fun process(call: PipelineContext<*, ApplicationCall>, entry: MeasureEntry) {

        for (handler in handlers) {
            handler(call, entry)
        }
    }

    companion object : ApplicationFeature<ApplicationCallPipeline, CallMeasurer, CallMeasurer> {
        override val key = AttributeKey<CallMeasurer>("Allow to measure stuff in calls")
        val CALL_START_TIME_KEY = AttributeKey<Long>("CALL_START_TIME_KEY")

        override fun install(pipeline: ApplicationCallPipeline, configure: CallMeasurer.() -> Unit): CallMeasurer {
            val feature = CallMeasurer().apply(configure)
            val phase = PipelinePhase("CallMeasurer")
            pipeline.insertPhaseBefore(ApplicationCallPipeline.Infrastructure, phase)
            pipeline.intercept(phase) {
                call.attributes[CALL_START_TIME_KEY] = System.currentTimeMillis()
                proceed()
            }

            pipeline.sendPipeline.intercept(ApplicationSendPipeline.After) {
                val now = System.currentTimeMillis()
                val start = call.attributes.getOrNull(CALL_START_TIME_KEY) ?: now
                val elapsed = now - start
                feature.process(this, MeasureEntry("total", null, elapsed))
            }
            return feature
        }
    }

    data class MeasureEntry(val name: String, val description: String?, val time: Long)
}

/**
 * Performs a measurement bound to an [ApplicationCall].
 */
suspend fun PipelineContext<*, ApplicationCall>.measure(name: String, description: String? = null, callback: suspend () -> Unit): Long {
    val measurer = application.installOrGet(CallMeasurer)

    val start = System.currentTimeMillis()
    val end: Long
    var exception: Throwable? = null
    try {
        callback()
    } catch (e: Throwable) {
        exception = e
    } finally {
        end = System.currentTimeMillis()
    }
    val elapsed = end - start

    measurer.process(this, CallMeasurer.MeasureEntry(name, description, elapsed))

    if (exception != null) {
        throw exception
    }

    return elapsed
}
