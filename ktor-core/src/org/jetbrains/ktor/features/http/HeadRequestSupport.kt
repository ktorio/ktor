package org.jetbrains.ktor.features.http

import org.jetbrains.ktor.application.*
import org.jetbrains.ktor.content.*
import org.jetbrains.ktor.features.*
import org.jetbrains.ktor.http.*
import org.jetbrains.ktor.util.*

object HeadRequestSupport : ApplicationFeature<ApplicationCallPipeline, Unit, Unit> {
    override val key = AttributeKey<Unit>("Automatic Head Response")

    override fun install(pipeline: ApplicationCallPipeline, configure: Unit.() -> Unit) {
        Unit.configure()

        pipeline.intercept(ApplicationCallPipeline.Infrastructure) {
            if (call.request.local.method == HttpMethod.Head) {
                it.response.pipeline.intercept(RespondPipeline.After) {
                    val message = subject.message
                    if (message is FinalContent && message !is FinalContent.NoContent) {
                        call.respond(HeadResponse(message))
                    }
                }
            }
        }
    }

    private class HeadResponse(val delegate: FinalContent) : FinalContent.NoContent() {
        override val headers by lazy { delegate.headers }
        override val status: HttpStatusCode? get() = delegate.status
    }
}
