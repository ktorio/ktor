package io.ktor.application

import io.ktor.pipeline.*
import io.ktor.request.*
import io.ktor.response.*

open class ApplicationCallPipeline : Pipeline<Unit, ApplicationCall>(Infrastructure, Call, Fallback) {
    /**
     * Pipeline for receiving content
     */
    val receivePipeline = ApplicationReceivePipeline()

    /**
     * Pipeline for sending content
     */
    val sendPipeline = ApplicationSendPipeline()

    companion object ApplicationPhase {
        val Infrastructure = PipelinePhase("Infrastructure")
        val Call = PipelinePhase("Call")
        val Fallback = PipelinePhase("Fallback")
    }
}
