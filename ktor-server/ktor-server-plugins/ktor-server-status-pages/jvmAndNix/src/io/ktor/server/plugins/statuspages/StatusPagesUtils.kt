/*
 * Copyright 2014-2022 JetBrains s.r.o and contributors. Use of this source code is governed by the Apache 2.0 license.
 */

package io.ktor.server.plugins.statuspages

import io.ktor.server.application.*
import io.ktor.util.pipeline.*
import kotlin.reflect.*

internal expect fun selectNearestParentClass(cause: Throwable, keys: List<KClass<*>>): KClass<*>?

internal object BeforeFallback : Hook<suspend (ServerCall) -> Unit> {
    override fun install(pipeline: ServerCallPipeline, handler: suspend (ServerCall) -> Unit) {
        val phase = PipelinePhase("BeforeFallback")
        pipeline.insertPhaseBefore(ServerCallPipeline.Fallback, phase)
        pipeline.intercept(phase) { handler(context) }
    }
}
