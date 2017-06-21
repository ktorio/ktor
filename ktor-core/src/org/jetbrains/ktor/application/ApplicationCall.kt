package org.jetbrains.ktor.application

import org.jetbrains.ktor.request.*
import org.jetbrains.ktor.response.*
import org.jetbrains.ktor.util.*

/**
 * Represents a single act of communication between client and server.
 */
interface ApplicationCall {
    /**
     * Application being called
     */
    val application: Application

    /**
     * Client request
     */
    val request: ApplicationRequest

    /**
     * Server response
     */
    val response: ApplicationResponse

    /**
     * Attributes attached to this instance
     */
    val attributes: Attributes

    /**
     * Parameters associated with this call
     */
    val parameters: ValuesMap

    /**
     * Pipeline for receiving content
     */
    val receivePipeline: ApplicationReceivePipeline

    /**
     * Pipeline for sending content
     */
    val sendPipeline: ApplicationSendPipeline
}

