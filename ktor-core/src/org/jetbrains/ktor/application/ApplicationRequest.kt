package org.jetbrains.ktor.application

import org.jetbrains.ktor.request.*
import org.jetbrains.ktor.http.*
import org.jetbrains.ktor.util.*

/**
 * Represents client's request
 */
interface ApplicationRequest {

    val call: ApplicationCall

    /**
     * HTTP request line
     */
    @Deprecated("Use localRoute or originRoute instead")
    val requestLine: HttpRequestLine
        get() = HttpRequestLine(localRoute.method, localRoute.uri, localRoute.version)

    /**
     * Parameters for this request
     */
    val parameters: ValuesMap

    /**
     * Headers for this request
     */
    val headers: ValuesMap

    val localRoute: RequestSocketRoute

    /**
     * Cookies for this request
     */
    val cookies: RequestCookies

    /**
     * Content for this request
     */
    val content: RequestContent
}