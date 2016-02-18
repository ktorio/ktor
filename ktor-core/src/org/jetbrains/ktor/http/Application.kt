package org.jetbrains.ktor.http

import org.jetbrains.ktor.application.*
import org.jetbrains.ktor.content.*
import java.nio.charset.*
import java.time.temporal.*

val ApplicationRequest.uri: String get() = requestLine.uri
val ApplicationRequest.httpMethod: HttpMethod get() = requestLine.method
val ApplicationRequest.httpVersion: String get() = requestLine.version
fun ApplicationRequest.header(name: String): String? = headers[name]
fun ApplicationRequest.parameter(name: String): String? = parameters[name]

fun ApplicationCall.respondRedirect(url: String, permanent: Boolean = false) {
    response.headers.append(HttpHeaders.Location, url)
    respond(if (permanent) HttpStatusCode.MovedPermanently else HttpStatusCode.Found)
}

fun ApplicationCall.respondStatus(code: HttpStatusCode, message: String = code.description) = respond(code)

fun ApplicationResponse.sendBytes(bytes: ByteArray) {
    status(HttpStatusCode.OK)
    streamBytes(bytes)
}

fun ApplicationCall.respondText(contentType: ContentType, text: String) = respond(TextContent(contentType, text))
fun ApplicationCall.respondText(text: String) = respondText(ContentType.Text.Plain, text)


