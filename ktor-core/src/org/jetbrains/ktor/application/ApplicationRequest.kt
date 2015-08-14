package org.jetbrains.ktor.application

import org.jetbrains.ktor.http.*
import org.jetbrains.ktor.interception.*

/** Established connection with client, encapsulates request and response facilities
 */
public interface ApplicationRequest {
    public val application: Application

    public val requestLine: HttpRequestLine
    public val parameters: Map<String, List<String>>
    public val headers: Map<String, String>
    public val body: String

    public val createResponse: Interceptable0<ApplicationResponse>
    public val close: Interceptable0<Unit>
}

public inline fun ApplicationRequest.createResponse(): ApplicationResponse = createResponse.call()
public inline fun ApplicationRequest.close(): Unit = close.call()
public fun ApplicationRequest.respond(handle: ApplicationResponse.() -> ApplicationRequestStatus): ApplicationRequestStatus {
    val response = createResponse()
    val result = response.handle()
    return result
}
