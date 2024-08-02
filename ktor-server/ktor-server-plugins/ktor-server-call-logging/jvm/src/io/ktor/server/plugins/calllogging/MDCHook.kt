/*
 * Copyright 2014-2022 JetBrains s.r.o and contributors. Use of this source code is governed by the Apache 2.0 license.
 */

package io.ktor.server.plugins.calllogging

import io.ktor.server.application.*
import io.ktor.server.response.*
import io.ktor.util.*
import io.ktor.util.pipeline.*

internal fun MDCHook(phase: PipelinePhase) = object : Hook<suspend (ServerCall, suspend () -> Unit) -> Unit> {
    override fun install(
        pipeline: ServerCallPipeline,
        handler: suspend (ServerCall, suspend () -> Unit) -> Unit
    ) {
        val mdcPhase = PipelinePhase("${phase.name}MDC")
        pipeline.insertPhaseBefore(phase, mdcPhase)

        pipeline.intercept(mdcPhase) {
            handler(call, ::proceed)
        }
    }
}

internal object ResponseSent : Hook<suspend (ServerCall) -> Unit> {
    override fun install(pipeline: ServerCallPipeline, handler: suspend (ServerCall) -> Unit) {
        pipeline.sendPipeline.intercept(ServerSendPipeline.Engine) {
            if (call.attributes.contains(responseSentMarker)) return@intercept

            call.attributes.put(responseSentMarker, Unit)
            proceed()
            handler(call)
        }
    }
}

private val responseSentMarker = AttributeKey<Unit>("ResponseSentTriggered")
