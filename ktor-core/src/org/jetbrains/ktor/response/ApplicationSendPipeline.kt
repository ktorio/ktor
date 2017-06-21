package org.jetbrains.ktor.response

import org.jetbrains.ktor.pipeline.*

open class ApplicationSendPipeline : Pipeline<Any>(Before, Transform, ContentEncoding, TransferEncoding, After, Host) {
    companion object Phases {
        val Before = PipelinePhase("Before")

        val Transform = PipelinePhase("Transform")

        val ContentEncoding = PipelinePhase("ContentEncoding")

        val TransferEncoding = PipelinePhase("TransferEncoding")

        val After = PipelinePhase("After")

        val Host = PipelinePhase("Host")
    }
}