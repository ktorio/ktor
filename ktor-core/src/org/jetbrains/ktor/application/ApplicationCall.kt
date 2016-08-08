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
     * Attributes attached to this instance
     */
    val attributes: Attributes

    /**
     * Closes this call and sends out any remaining data
     */
    override fun close(): Unit

    val parameters: ValuesMap

    /**
     * Server response
     */
    val response: ApplicationResponse

    fun respond(message: Any): Nothing

    @Deprecated("Use response.pipeline instead", ReplaceWith("response.pipeline"))
    val respond: RespondPipeline
        get() = response.pipeline

    val execution: PipelineMachine
}
