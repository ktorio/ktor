package org.jetbrains.ktor.application

import org.jetbrains.ktor.pipeline.*
import org.jetbrains.ktor.util.*

val PipelineContext<ApplicationCall>.call: ApplicationCall get() = subject

open class ApplicationCallPipeline() : Pipeline<ApplicationCall>(Infrastructure, Call, Fallback) {
    companion object ApplicationPhase {
        val Infrastructure = PipelinePhase("Infrastructure")
        val Call = PipelinePhase("Call")
        val Fallback = PipelinePhase("Fallback")
    }
}

class ResponseMessage(val call: ApplicationCall, message: Any) {
    var message: Any = message
        internal set

    val attributes = Attributes()
}

open class RespondPipeline : Pipeline<ResponseMessage>(Before, Transform, Render, ContentEncoding, TransferEncoding, After) {
    companion object RespondPhase {
        val Before = PipelinePhase("Before")

        val Transform = PipelinePhase("Transform")

        val Render = PipelinePhase("Render")

        val ContentEncoding = PipelinePhase("ContentEncoding")

        val TransferEncoding = PipelinePhase("TransferEncoding")

        val After = PipelinePhase("After")
    }
}

val PipelineContext<ResponseMessage>.call: ApplicationCall
    @JvmName("getCallFromRespondPipeline")
    get() = subject.call
