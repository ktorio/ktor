/*
 * Copyright 2014-2022 JetBrains s.r.o and contributors. Use of this source code is governed by the Apache 2.0 license.
 */

package io.ktor.server.sessions

import io.ktor.server.application.*
import io.ktor.server.response.*

internal object BeforeSend : Hook<suspend (ServerCall) -> Unit> {
    override fun install(pipeline: ServerCallPipeline, handler: suspend (ServerCall) -> Unit) {
        pipeline.sendPipeline.intercept(ServerSendPipeline.Before) {
            handler(call)
        }
    }
}
