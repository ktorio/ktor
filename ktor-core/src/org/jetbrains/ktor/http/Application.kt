package org.jetbrains.ktor.http

import org.jetbrains.ktor.application.*

val ApplicationRequest.uri: String get() = requestLine.uri
val ApplicationRequest.httpMethod: String get() = requestLine.method
val ApplicationRequest.httpVersion: String get() = requestLine.version
fun ApplicationRequest.header(name: String): String? = headers[name]
fun ApplicationRequest.parameter(name: String): String? = parameters[name]?.singleOrNull()

fun ApplicationResponse.status(code: HttpStatusCode) = status(code.value)
fun ApplicationResponse.contentType(value: ContentType) = contentType(value.toString())
fun ApplicationResponse.contentType(value: String) = header("Content-Type", value)
fun ApplicationResponse.header(name: String, value: Int): ApplicationResponse = header(name, value.toString())

fun ApplicationResponse.sendRedirect(url: String, permanent: Boolean = false): ApplicationRequestStatus {
    status(if (permanent) HttpStatusCode.MovedPermanently else HttpStatusCode.Found)
    header("Location", url)
    return send()
}

fun ApplicationRequest.respondText(text: String) = respond {
    status(HttpStatusCode.OK)
    contentType(ContentType.Text.Plain)
    content(text)
    send()
}

fun ApplicationRequest.respondRedirect(url: String, permanent: Boolean = false) = respond { sendRedirect(url, permanent) }
fun ApplicationRequest.respondError(code: Int, message: String) = respond {
    status(code)
    content(message)
    send()
}

fun ApplicationRequest.respondAuthenticationRequest(realm: String) = respond {
    status(HttpStatusCode.Unauthorized)
    content("Not authorized")
    header("WWW-Authenticate", "Basic realm=\"$realm\"")
    send()
}

