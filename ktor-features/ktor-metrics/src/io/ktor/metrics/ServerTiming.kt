package io.ktor.metrics

import io.ktor.application.*
import io.ktor.http.*
import io.ktor.response.*
import io.ktor.util.*

class ServerTiming {
    companion object : ApplicationFeature<ApplicationCallPipeline, ServerTiming, Unit> {
        override val key = AttributeKey<Unit>("Server-Timing header support")

        override fun install(pipeline: ApplicationCallPipeline, configure: ServerTiming.() -> Unit): Unit {
            pipeline.installOrGet(CallMeasurer) {
                addHandler { entry ->
                    call.response.header("Server-Timing", buildString {
                        append(entry.name)
                        if (entry.description != null) {
                            append(";desc=")
                            append(entry.description.quote())
                        }
                        append(";dur=")
                        append(entry.time.toDouble() / 1000.0)
                    })
                }
            }
        }
    }
}
