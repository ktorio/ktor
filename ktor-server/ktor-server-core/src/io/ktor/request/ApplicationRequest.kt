package io.ktor.request

import io.ktor.application.*
import io.ktor.content.*
import io.ktor.http.*
import io.ktor.util.*

/**
 * Represents client's request
 */
interface ApplicationRequest {
    /**
     * [ApplicationCall] instance this ApplicationRequest is attached to
     */
    val call: ApplicationCall

    /**
     * Pipeline for receiving content
     */
    val pipeline: ApplicationReceivePipeline

    /**
     * Parameters provided in an URL
     */
    val queryParameters: StringValues

    /**
     * Headers for this request
     */
    val headers: StringValues

    /**
     * Contains http request and connection details such as a host name used to connect, port, scheme and so on.
     * No proxy headers could affect it. Use [ApplicationRequest.origin] if you need override headers support
     */
    val local: RequestConnectionPoint

    /**
     * Cookies for this request
     */
    val cookies: RequestCookies

    fun receiveContent(): IncomingContent
}
