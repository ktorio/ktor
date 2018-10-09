package io.ktor.server.engine

import io.ktor.application.*
import io.ktor.util.pipeline.*

/**
 * Application engine pipeline. One usually don't need to install interceptors here unless your are writing
 * your own engine implementation
 */
class EnginePipeline : Pipeline<Unit, ApplicationCall>(Before, Call) {
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

