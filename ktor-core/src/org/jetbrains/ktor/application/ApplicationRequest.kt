package org.jetbrains.ktor.application

import org.jetbrains.ktor.http.*
import org.jetbrains.ktor.request.*
import org.jetbrains.ktor.util.*

/**
 * Represents client's request
 */
interface ApplicationRequest {

    @Deprecated("Pass call instead of request to here. To be removed.", level = DeprecationLevel.ERROR)
    val call: ApplicationCall
        get() = throw UnsupportedOperationException("Deprecated")

    /**
     * HTTP request line
     */
    @Deprecated("Use local or origin instead", level = DeprecationLevel.ERROR)
    val requestLine: @Suppress("DEPRECATION") HttpRequestLine
        get() = throw UnsupportedOperationException("Deprecated")

    /**
     * Parameters for this request
     */
    @Deprecated("Use queryParameters or content instead")
    val parameters: ValuesMap
        get() = queryParameters + content.get()

    val queryParameters: ValuesMap

    /**
     * Headers for this request
     */
    val headers: ValuesMap

    /**
     * Attributes attached to this instance
     */
    val attributes: Attributes

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