package org.jetbrains.ktor.application

import org.jetbrains.ktor.request.*
import org.jetbrains.ktor.http.*
import org.jetbrains.ktor.util.*

/**
 * Represents client's request
 */
interface ApplicationRequest {

    @Deprecated("Pass call instead of request to here. To be removed.")
    val call: ApplicationCall

    /**
     * HTTP request line
     */
    @Deprecated("Use local or origin instead")
    val requestLine: HttpRequestLine
        get() = HttpRequestLine(local.method, local.uri, local.version)

    /**
     * Parameters for this request
     */
    val parameters: ValuesMap

    /**
     * Headers for this request
     */
    val headers: ValuesMap

    /**
     * Contains http request and connection details such as a host name used to connect, port, scheme and so on.
     * No proxy headers could affect it. Use [ApplicationRequest.origin] if you need override headers support
     */
    val local: RequestConnectionPoint

    /**
     * Cookies for this request
     */
    val cookies: RequestCookies

    /**
     * Content for this request
     */
    val content: RequestContent
}