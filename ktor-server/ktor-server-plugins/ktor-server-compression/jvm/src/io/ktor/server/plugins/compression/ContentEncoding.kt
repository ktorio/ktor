/*
 * Copyright 2014-2024 JetBrains s.r.o and contributors. Use of this source code is governed by the Apache 2.0 license.
 */

package io.ktor.server.plugins.compression

import io.ktor.http.content.*
import io.ktor.server.application.*
import io.ktor.server.response.*
import io.ktor.util.pipeline.*

internal object ContentEncoding : Hook<suspend ContentEncoding.Context.(PipelineCall) -> Unit> {

    class Context(private val pipelineContext: PipelineContext<Any, PipelineCall>) {
        fun transformBody(block: (OutgoingContent) -> OutgoingContent?) {
            val transformedContent = block(pipelineContext.subject as OutgoingContent)
            if (transformedContent != null) {
                pipelineContext.subject = transformedContent
            }
        }
    }

    override fun install(
        pipeline: ApplicationCallPipeline,
        handler: suspend Context.(PipelineCall) -> Unit
    ) {
        pipeline.sendPipeline.intercept(ApplicationSendPipeline.ContentEncoding) {
            handler(Context(this), call)
        }
    }
}
