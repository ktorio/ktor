package org.jetbrains.ktor.application

import org.jetbrains.ktor.http.*
import java.io.*

/** Established connection with client, encapsulates request and response facilities
 */
public interface ApplicationRequest {
    public val application: Application

    public val requestLine: HttpRequestLine
    public val parameters: Map<String, List<String>>
    public val headers: Map<String, String>

    public fun respond(handle: ApplicationResponse.() -> ApplicationRequestStatus): ApplicationRequestStatus
}

val ApplicationRequest.uri: String get() = requestLine.uri
val ApplicationRequest.method: String get() = requestLine.method
val ApplicationRequest.version: String get() = requestLine.version
fun ApplicationRequest.header(name: String): String? = headers[name]
fun ApplicationRequest.parameter(name: String): String? = headers[name]

public interface ApplicationResponse {
    public fun header(name: String, value: String): ApplicationResponse
    public fun status(code: Int): ApplicationResponse
    public fun content(text: String, encoding: String = "UTF-8"): ApplicationResponse
    public fun content(bytes: ByteArray): ApplicationResponse
    public fun contentStream(streamer: Writer.() -> Unit): ApplicationResponse

    public fun send(): ApplicationRequestStatus
    public fun sendRedirect(url: String): ApplicationRequestStatus
}

fun ApplicationResponse.header(name: String, value: Int): ApplicationResponse = header(name, value.toString())













