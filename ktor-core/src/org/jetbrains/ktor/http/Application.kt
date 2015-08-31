package org.jetbrains.ktor.http

import org.jetbrains.ktor.application.*
import java.time.temporal.*

val ApplicationRequest.uri: String get() = requestLine.uri
val ApplicationRequest.httpMethod: HttpMethod get() = requestLine.method
val ApplicationRequest.httpVersion: String get() = requestLine.version
fun ApplicationRequest.header(name: String): String? = headers[name]?.singleOrNull()
fun ApplicationRequest.parameter(name: String): String? = parameters[name]?.singleOrNull()

fun ApplicationResponse.status(code: HttpStatusCode) = status(code.value)
fun ApplicationResponse.contentType(value: ContentType) = contentType(value.toString())
fun ApplicationResponse.contentType(value: String) = header(HttpHeaders.ContentType, value)
fun ApplicationResponse.header(name: String, value: Int) = header(name, value.toString())
fun ApplicationResponse.header(name: String, date: Temporal) = header(name, date.toHttpDateString())

fun ApplicationResponse.sendRedirect(url: String, permanent: Boolean = false): ApplicationRequestStatus {
    status(if (permanent) HttpStatusCode.MovedPermanently else HttpStatusCode.Found)
    header(HttpHeaders.Location, url)
    return ApplicationRequestStatus.Handled
}

fun ApplicationResponse.sendError(code: Int, message: String): ApplicationRequestStatus {
    status(code)
    streamText(message)
    return ApplicationRequestStatus.Handled
}

fun ApplicationResponse.sendError(code: HttpStatusCode, message: String = code.description): ApplicationRequestStatus {
    return sendError(code.value, message)
}

fun ApplicationResponse.sendAuthenticationRequest(realm: String): ApplicationRequestStatus {
    status(HttpStatusCode.Unauthorized)
    header(HttpHeaders.WWWAuthenticate, "Basic realm=\"$realm\"")
    streamText("Not authorized")
    return ApplicationRequestStatus.Handled
}

