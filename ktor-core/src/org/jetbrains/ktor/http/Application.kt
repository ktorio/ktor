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

fun ApplicationResponse.sendRedirect(url: String, permanent: Boolean = false): ApplicationCallResult {
    headers.append(HttpHeaders.Location, url)
    return send(if (permanent) HttpStatusCode.MovedPermanently else HttpStatusCode.Found)
}

fun ApplicationResponse.sendError(code: HttpStatusCode, message: String = code.description): ApplicationCallResult {
    return send(TextErrorContent(code, message))
}

public fun ApplicationResponse.sendBytes(bytes: ByteArray): ApplicationCallResult {
    status(HttpStatusCode.OK)
    streamBytes(bytes)
    return ApplicationCallResult.Handled
}

public fun ApplicationResponse.sendText(contentType: ContentType, text: String): ApplicationCallResult {
    return send(TextContent(contentType, text))
}

public fun ApplicationResponse.sendText(text: String): ApplicationCallResult {
    return sendText(ContentType.Text.Plain, text)
}


