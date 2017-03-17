package org.jetbrains.ktor.application

import org.jetbrains.ktor.http.*
import org.jetbrains.ktor.pipeline.*
import org.jetbrains.ktor.response.*

/**
 * Represents server's response
 */
interface ApplicationResponse {
    /**
     * [ApplicationCall] instance this ApplicationResponse is attached to
     */
    val call: ApplicationCall

    val pipeline: ApplicationResponsePipeline
    val headers: ResponseHeaders
    val cookies: ResponseCookies

    fun status(): HttpStatusCode?
    fun status(value: HttpStatusCode)

    /**
     * Produces HTTP/2 push from server to client or sets HTTP/1.x hint header
     * or does nothing (may call or not call [block]).
     * Exact behaviour is up to host implementation.
     */
    fun push(block: ResponsePushBuilder.() -> Unit) {
    }
}

open class ApplicationResponsePipeline : Pipeline<Any>(Before, Transform, Render, ContentEncoding, TransferEncoding, After) {
    companion object RespondPhase {
        val Before = PipelinePhase("Before")

        val Transform = PipelinePhase("Transform")

        val Render = PipelinePhase("Render")

        val ContentEncoding = PipelinePhase("ContentEncoding")

        val TransferEncoding = PipelinePhase("TransferEncoding")

        val After = PipelinePhase("After")
    }
}

