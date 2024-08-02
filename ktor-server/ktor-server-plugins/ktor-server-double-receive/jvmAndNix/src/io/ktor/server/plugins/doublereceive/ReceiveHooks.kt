/*
 * Copyright 2014-2022 JetBrains s.r.o and contributors. Use of this source code is governed by the Apache 2.0 license.
 */

package io.ktor.server.plugins.doublereceive

import io.ktor.server.application.*
import io.ktor.server.request.*

internal object ReceiveBytes : Hook<suspend (ServerCall, Any) -> Any> {
    override fun install(
        pipeline: ServerCallPipeline,
        handler: suspend (ServerCall, Any) -> Any
    ) {
        pipeline.receivePipeline.intercept(ServerReceivePipeline.Before) {
            val body = handler(call, it)
            proceedWith(body)
        }
    }
}

internal object ReceiveBodyTransformed : Hook<suspend (ServerCall, Any) -> Any> {
    override fun install(
        pipeline: ServerCallPipeline,
        handler: suspend (call: ServerCall, state: Any) -> Any
    ) {
        pipeline.receivePipeline.intercept(ServerReceivePipeline.After) {
            val body = handler(call, it)
            proceedWith(body)
        }
    }
}
