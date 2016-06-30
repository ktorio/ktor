package org.jetbrains.ktor.application

import org.jetbrains.ktor.pipeline.*
import org.jetbrains.ktor.util.*
import java.io.*

/**
 * Represents a single act of communication between client and server.
 */
interface ApplicationCall : Closeable {
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
     * Closes this call and sends out any remaining data
     */
    override fun close(): Unit

    val parameters: ValuesMap

    fun respond(message: Any): Nothing

    val respond: RespondPipeline
    val execution: PipelineMachine
}
