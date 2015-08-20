package org.jetbrains.ktor.http

import org.jetbrains.ktor.application.*

val ApplicationRequest.uri: String get() = requestLine.uri
val ApplicationRequest.httpMethod: HttpMethod get() = requestLine.method
val ApplicationRequest.httpVersion: String get() = requestLine.version
fun ApplicationRequest.header(name: String): String? = headers[name]
fun ApplicationRequest.parameter(name: String): String? = parameters[name]?.singleOrNull()

fun ApplicationResponse.status(code: HttpStatusCode) = status(code.value)
fun ApplicationResponse.contentType(value: ContentType) = contentType(value.toString())
fun ApplicationResponse.contentType(value: String) = header("Content-Type", value)
fun ApplicationResponse.header(name: String, value: Int) = header(name, value.toString())

fun ApplicationResponse.sendRedirect(url: String, permanent: Boolean = false): ApplicationRequestStatus {
    status(if (permanent) HttpStatusCode.MovedPermanently else HttpStatusCode.Found)
    header("Location", url)
    return ApplicationRequestStatus.Handled
}

fun ApplicationResponse.sendError(code: Int, message: String): ApplicationRequestStatus {
    status(code)
    streamText(message)
    return ApplicationRequestStatus.Handled
}

fun ApplicationResponse.sendAuthenticationRequest(realm: String): ApplicationRequestStatus {
    status(HttpStatusCode.Unauthorized)
    header("WWW-Authenticate", "Basic realm=\"$realm\"")
    streamText("Not authorized")
    return ApplicationRequestStatus.Handled
}

