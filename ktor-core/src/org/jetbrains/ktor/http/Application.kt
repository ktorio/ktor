package org.jetbrains.ktor.http

import org.jetbrains.ktor.application.*
import org.jetbrains.ktor.content.*

val ApplicationRequest.uri: String get() = requestLine.uri
val ApplicationRequest.httpMethod: HttpMethod get() = requestLine.method
val ApplicationRequest.httpVersion: String get() = requestLine.version
fun ApplicationRequest.header(name: String): String? = headers[name]
fun ApplicationRequest.parameter(name: String): String? = parameters[name]

fun ApplicationCall.respondRedirect(url: String, permanent: Boolean = false): Nothing {
    response.headers.append(HttpHeaders.Location, url)
    respond(if (permanent) HttpStatusCode.MovedPermanently else HttpStatusCode.Found)
}

fun ApplicationResponse.sendBytes(bytes: ByteArray) {
    status(HttpStatusCode.OK)
    streamBytes(bytes)
}

fun ApplicationCall.respondText(contentType: ContentType, text: String): Nothing = respond(TextContent(contentType, text))
fun ApplicationCall.respondText(text: String): Nothing = respondText(ContentType.Text.Plain, text)


