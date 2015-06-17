package org.jetbrains.ktor.application

import java.io.*

/** Established connection with client, encapsulates request and response facilities
 */
public interface ApplicationRequest {
    public val application: Application

    public val uri: String
    public val httpMethod: String

    public val parameters: Map<String, List<String>>
    public fun header(name: String): String?
    public fun headers(): Map<String, String>

    public fun hasResponse(): Boolean
    public fun response(): ApplicationResponse
    public fun response(body: ApplicationResponse.() -> Unit): ApplicationResponse
}

public interface ApplicationResponse {
    public fun header(name: String, value: String): ApplicationResponse
    public fun header(name: String, value: Int): ApplicationResponse
    public fun status(code: Int): ApplicationResponse
    public fun contentType(value: String): ApplicationResponse
    public fun content(text: String, encoding: String = "UTF-8"): ApplicationResponse
    public fun content(bytes: ByteArray): ApplicationResponse
    public fun contentStream(streamer: Writer.() -> Unit): ApplicationResponse
    public fun send()
    public fun sendRedirect(url: String)
}














