package org.jetbrains.ktor.response

import org.jetbrains.ktor.application.*
import org.jetbrains.ktor.http.*

/**
 * Represents server's response
 */
interface ApplicationResponse {
    /**
     * [ApplicationCall] instance this ApplicationResponse is attached to
     */
    val call: ApplicationCall

    /**
     * Headers for this response
     */
    val headers: ResponseHeaders

    /**
     * Cookies for this response
     */
    val cookies: ResponseCookies

    /**
     * Currently set status code for this response, or null if none was set
     */
    fun status(): HttpStatusCode?

    /**
     * Set status for this response
     */
    fun status(value: HttpStatusCode)

    /**
     * Produces HTTP/2 push from server to client or sets HTTP/1.x hint header
     * or does nothing (may call or not call [block]).
     * Exact behaviour is up to host implementation.
     */
    fun push(block: ResponsePushBuilder.() -> Unit) {}
}



