/*
 * Copyright 2014-2026 JetBrains s.r.o and contributors. Use of this source code is governed by the Apache 2.0 license.
 */

package io.ktor.server.plugins.compression

import io.ktor.server.application.*
import io.ktor.server.request.ApplicationReceivePipeline
import io.ktor.util.pipeline.*
import io.ktor.utils.io.ByteReadChannel

internal object ContentDecoding : Hook<suspend ContentDecoding.Context.(PipelineCall) -> Unit> {

    private val contentDecoding = PipelinePhase("ContentDecoding")

    class Context(private val pipelineContext: PipelineContext<Any, PipelineCall>) {
        suspend fun transformBody(block: suspend (ByteReadChannel) -> ByteReadChannel?) {
            val transformedContent = block(pipelineContext.subject as? ByteReadChannel ?: return)
            if (transformedContent != null) {
                pipelineContext.subject = transformedContent
            }
        }
    }

    override fun install(
        pipeline: ApplicationCallPipeline,
        handler: suspend Context.(PipelineCall) -> Unit
    ) {
        pipeline.receivePipeline.insertPhaseBefore(ApplicationReceivePipeline.Transform, contentDecoding)
        pipeline.receivePipeline.intercept(contentDecoding) {
            handler(Context(this), call)
        }
    }
}
