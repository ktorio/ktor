/*
 * Copyright 2014-2021 JetBrains s.r.o and contributors. Use of this source code is governed by the Apache 2.0 license.
 */

package io.ktor.server.transformcheck

import io.ktor.http.*
import io.ktor.http.content.*
import io.ktor.server.application.*
import io.ktor.server.http.content.*
import io.ktor.server.plugins.*
import io.ktor.server.request.*
import io.ktor.server.response.*
import io.ktor.server.routing.*
import io.ktor.util.*
import io.ktor.util.converters.*

public class TransformationChecker {

    public class Configuration

    public companion object Plugin :
        ApplicationPlugin<ApplicationCallPipeline, TransformationChecker.Configuration, TransformationChecker> {

        override fun install(
            pipeline: ApplicationCallPipeline,
            configure: Configuration.() -> Unit
        ): TransformationChecker {
            val plugin = TransformationChecker()

            // Respond with "415 Unsupported Media Type" if content cannot be transformed on receive
            pipeline.intercept(ApplicationCallPipeline.Plugins) {
                try {
                    proceed()
                } catch (e: UnsupportedMediaTypeException) {
                    call.respond(HttpStatusCode.UnsupportedMediaType)
                }
            }

            pipeline.sendPipeline.intercept(ApplicationSendPipeline.After) { subject ->
                if(subject !is OutgoingContent) {
                    proceedWith(HttpStatusCodeContent(HttpStatusCode.NotAcceptable))
                }
            }

            pipeline.receivePipeline.intercept(ApplicationReceivePipeline.After) { subject ->
                val requestContentType = try {
                    call.request.contentType().withoutParameters()
                } catch (parseFailure: BadContentTypeFormatException) {
                    throw BadRequestException(
                        "Illegal Content-Type header format: ${call.request.headers[HttpHeaders.ContentType]}",
                        parseFailure
                    )
                }

                if(!subject.typeInfo.type.isInstance(subject.value)) {
                    throw UnsupportedMediaTypeException(requestContentType)
                }
            }
            return plugin
        }

        override val key: AttributeKey<TransformationChecker> = AttributeKey("TransformationChecker")
    }
}
