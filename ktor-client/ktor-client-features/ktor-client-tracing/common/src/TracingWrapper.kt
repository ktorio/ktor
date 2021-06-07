/*
* Copyright 2014-2021 JetBrains s.r.o and contributors. Use of this source code is governed by the Apache 2.0 license.
*/

package io.ktor.client.features.tracing

import io.ktor.client.engine.*

/**
 * Tracing wrapper that wraps [HttpClientEngineFactory] and creates [EngineWithTracer] instead of original engine.
 */
class TracingWrapper<T : HttpClientEngineConfig>(
    private val delegate: HttpClientEngineFactory<T>,
    private val tracer: Tracer
) : HttpClientEngineFactory<T> {

    override fun create(block: T.() -> Unit): HttpClientEngine {
        val engine = delegate.create(block)
        return EngineWithTracer(engine, tracer)
    }
}
