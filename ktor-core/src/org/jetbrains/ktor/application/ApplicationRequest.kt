package org.jetbrains.ktor.application

import org.jetbrains.ktor.http.*
import org.jetbrains.ktor.util.*

/** Established connection with client, encapsulates request and response facilities
 */
public interface ApplicationRequest {
    val requestLine: HttpRequestLine
    val parameters: ValuesMap
    val headers: ValuesMap
    val cookies: RequestCookies
    val content: ApplicationRequestContent
}