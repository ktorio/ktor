/*
 * Copyright 2014-2019 JetBrains s.r.o and contributors. Use of this source code is governed by the Apache 2.0 license.
 */

package io.ktor.server.engine

import io.ktor.application.*
import io.ktor.request.*
import io.ktor.response.*
import io.ktor.util.pipeline.*

/**
 * Application engine pipeline. One usually don't need to install interceptors here unless your are writing
 * your own engine implementation
 */
public class EnginePipeline : Pipeline<Unit, ApplicationCall>(Before, Call) {
    /**
     * Pipeline for receiving content
     */
    public val receivePipeline: ApplicationReceivePipeline = ApplicationReceivePipeline()

    /**
     * Pipeline for sending content
     */
    public val sendPipeline: ApplicationSendPipeline = ApplicationSendPipeline()

    public companion object {
        /**
         * Before call phase
         */
        public val Before: PipelinePhase = PipelinePhase("before")

        /**
         * Application call pipeline phase
         */
        public val Call: PipelinePhase = PipelinePhase("call")
    }
}

