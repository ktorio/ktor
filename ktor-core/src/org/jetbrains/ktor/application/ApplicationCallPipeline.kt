package org.jetbrains.ktor.application

import org.jetbrains.ktor.pipeline.*
import org.jetbrains.ktor.request.*
import org.jetbrains.ktor.response.*

open class ApplicationCallPipeline : Pipeline<Unit>(Infrastructure, Call, Fallback) {
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
