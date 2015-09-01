package org.jetbrains.ktor.http

import org.jetbrains.ktor.application.*
import java.nio.charset.*
import java.time.temporal.*

val ApplicationRequest.uri: String get() = requestLine.uri
val ApplicationRequest.httpMethod: HttpMethod get() = requestLine.method
val ApplicationRequest.httpVersion: String get() = requestLine.version
fun ApplicationRequest.header(name: String): String? = headers[name]?.singleOrNull()
fun ApplicationRequest.parameter(name: String): String? = parameters[name]?.singleOrNull()

val ApplicationRequest.contentCharset: Charset?
    get() = contentType().parameter("charset")?.let { Charset.forName(it) }

fun ApplicationResponse.contentType(value: ContentType) = contentType(value.toString())
fun ApplicationResponse.contentType(value: String) = headers.append(HttpHeaders.ContentType, value)
fun ApplicationResponse.header(name: String, value: Int) = headers.append(name, value.toString())
fun ApplicationResponse.header(name: String, date: Temporal) = headers.append(name, date.toHttpDateString())

fun ApplicationResponse.sendRedirect(url: String, permanent: Boolean = false): ApplicationRequestStatus {
    status(if (permanent) HttpStatusCode.MovedPermanently else HttpStatusCode.Found)
    headers.append(HttpHeaders.Location, url)
    return ApplicationRequestStatus.Handled
}

fun ApplicationResponse.sendError(code: HttpStatusCode, message: String = code.description): ApplicationRequestStatus {
    status(code)
    streamText(message)
    return ApplicationRequestStatus.Handled
}

fun ApplicationResponse.sendAuthenticationRequest(realm: String): ApplicationRequestStatus {
    status(HttpStatusCode.Unauthorized)
    headers.append(HttpHeaders.WWWAuthenticate, "Basic realm=\"$realm\"")
    streamText("Not authorized")
    return ApplicationRequestStatus.Handled
}

