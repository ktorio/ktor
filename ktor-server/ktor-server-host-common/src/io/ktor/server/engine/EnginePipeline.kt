package io.ktor.server.engine

import io.ktor.application.*
import io.ktor.request.*
import io.ktor.response.*
import io.ktor.util.pipeline.*

/**
 * Application engine pipeline. One usually don't need to install interceptors here unless your are writing
 * your own engine implementation
 */
class EnginePipeline : Pipeline<Unit, ApplicationCall>(Before, Call) {
    /**
     * Pipeline for receiving content
     */
    val receivePipeline = ApplicationReceivePipeline()

    /**
     * Pipeline for sending content
     */
    val sendPipeline = ApplicationSendPipeline()

    companion object {
        /**
         * Before call phase
         */
        val Before = PipelinePhase("before")

        /**
         * Application call pipeline phase
         */
        val Call = PipelinePhase("call")
    }
}

