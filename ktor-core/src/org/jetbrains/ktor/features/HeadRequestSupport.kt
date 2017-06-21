package org.jetbrains.ktor.features

import org.jetbrains.ktor.application.*
import org.jetbrains.ktor.content.*
import org.jetbrains.ktor.http.*
import org.jetbrains.ktor.pipeline.*
import org.jetbrains.ktor.response.*
import org.jetbrains.ktor.util.*

object HeadRequestSupport : ApplicationFeature<ApplicationCallPipeline, Unit, Unit> {
    val HeadPhase = PipelinePhase("HEAD")

    override val key = AttributeKey<Unit>("Automatic Head Response")

    override fun install(pipeline: ApplicationCallPipeline, configure: Unit.() -> Unit) {
        Unit.configure()

        pipeline.intercept(ApplicationCallPipeline.Infrastructure) {
            if (call.request.local.method == HttpMethod.Head) {
                call.sendPipeline.phases.insertBefore(ApplicationSendPipeline.TransferEncoding, HeadPhase)
                call.sendPipeline.intercept(HeadPhase) { message ->
                    if (message is FinalContent && message !is FinalContent.NoContent) {
                        proceedWith(HeadResponse(message))
                    }
                }

                // Pretend the request was with GET method so that all normal routes and interceptors work
                // but in the end we will drop the content
                call.mutableOriginConnectionPoint.method = HttpMethod.Get
            }
        }
    }

    private class HeadResponse(val delegate: FinalContent) : FinalContent.NoContent() {
        override val headers by lazy { delegate.headers }
        override val status: HttpStatusCode? get() = delegate.status
    }
}
