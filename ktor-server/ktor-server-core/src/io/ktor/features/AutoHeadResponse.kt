package io.ktor.features

import io.ktor.application.*
import io.ktor.http.content.*
import io.ktor.http.*
import io.ktor.pipeline.*
import io.ktor.response.*
import io.ktor.util.*

object AutoHeadResponse : ApplicationFeature<ApplicationCallPipeline, Unit, Unit> {
    private val HeadPhase = PipelinePhase("HEAD")

    override val key = AttributeKey<Unit>("Automatic Head Response")

    override fun install(pipeline: ApplicationCallPipeline, configure: Unit.() -> Unit) {
        Unit.configure()

        pipeline.intercept(ApplicationCallPipeline.Infrastructure) {
            if (call.request.local.method == HttpMethod.Head) {
                call.response.pipeline.insertPhaseBefore(ApplicationSendPipeline.TransferEncoding, HeadPhase)
                call.response.pipeline.intercept(HeadPhase) { message ->
                    if (message is OutgoingContent && message !is OutgoingContent.NoContent) {
                        proceedWith(HeadResponse(message))
                    }
                }

                // Pretend the request was with GET method so that all normal routes and interceptors work
                // but in the end we will drop the content
                call.mutableOriginConnectionPoint.method = HttpMethod.Get
            }
        }
    }

    private class HeadResponse(val original: OutgoingContent) : OutgoingContent.NoContent() {
        override val status: HttpStatusCode? get() = original.status
        override val contentType: ContentType? get() = original.contentType
        override val contentLength: Long? get() = original.contentLength
        override fun <T : Any> getProperty(key: AttributeKey<T>) = original.getProperty(key)
        override fun <T : Any> setProperty(key: AttributeKey<T>, value: T?) = original.setProperty(key, value)
        override val headers get() = original.headers
    }
}
