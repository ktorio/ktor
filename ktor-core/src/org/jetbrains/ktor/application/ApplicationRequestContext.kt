package org.jetbrains.ktor.application

import org.jetbrains.ktor.interception.*
import org.jetbrains.ktor.util.*

// TODO: rename to ApplicationCall
// Other words: Interaction, Call, Exchange
/**
 * Represents a single act of communication between client and server.
 */
public interface ApplicationRequestContext {
    /**
     * Application being called
     */
    public val application: Application

    /**
     * Client request
     */
    public val request: ApplicationRequest

    /**
     * Server response
     */
    public val response: ApplicationResponse

    /**
     * Attributes attached to this instance
     */
    public val attributes: Attributes

    /**
     * Closes this call and sends out any remaining data
     *
     * Can be intercepted to perform any cleanup tasks that are required by the processing pipeline
     */
    public val close: Interceptable0<Unit>
}

/**
 * Closes this call and sends out any remaining data
 */
public fun ApplicationRequestContext.close(): Unit = close.call()
