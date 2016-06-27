package org.jetbrains.ktor.application

import org.jetbrains.ktor.request.*
import org.jetbrains.ktor.http.*
import org.jetbrains.ktor.util.*

/**
 * Represents client's request
 */
interface ApplicationRequest {
    /**
     * HTTP request line
     */
    val requestLine: HttpRequestLine

    /**
     * Parameters for this request
     */
    val parameters: ValuesMap

    /**
     * Headers for this request
     */
    val headers: ValuesMap

    /**
     * Cookies for this request
     */
    val cookies: RequestCookies

    /**
     * Content for this request
     */
    val content: RequestContent
}