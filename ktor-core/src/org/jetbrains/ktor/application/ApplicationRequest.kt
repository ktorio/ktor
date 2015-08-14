package org.jetbrains.ktor.application

import org.jetbrains.ktor.http.*
import org.jetbrains.ktor.interception.*
import java.io.*

/** Established connection with client, encapsulates request and response facilities
 */
public interface ApplicationRequest {
    public val application: Application

    public val requestLine: HttpRequestLine
    public val parameters: Map<String, List<String>>
    public val headers: Map<String, String>
    public val body: String

    public val createResponse: Interceptable0<ApplicationResponse>
}

public inline fun ApplicationRequest.createResponse(): ApplicationResponse = createResponse.call()

public interface ApplicationResponse {
    public val header: Interceptable2<String, String, ApplicationResponse>
    public val status: Interceptable1<Int, ApplicationResponse>

    public fun content(text: String, encoding: String = "UTF-8"): ApplicationResponse
    public fun content(bytes: ByteArray): ApplicationResponse
    public fun contentStream(streamer: Writer.() -> Unit): ApplicationResponse

    public fun send(): ApplicationRequestStatus
}

public inline fun ApplicationResponse.header(name: String, value: String): ApplicationResponse = header.call(name, value)
public inline fun ApplicationResponse.status(code: Int): ApplicationResponse = status.call(code)

public fun ApplicationRequest.respond(handle: ApplicationResponse.() -> ApplicationRequestStatus): ApplicationRequestStatus {
    val response = createResponse()
    val result = response.handle()
    return result
}
