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

val ApplicationRequest.contentCharset: Charset?
    get() = contentType().parameter("charset")?.let { Charset.forName(it) }

fun ApplicationResponse.contentType(value: ContentType) = contentType(value.toString())
fun ApplicationResponse.contentType(value: String) = headers.append(HttpHeaders.ContentType, value)
fun ApplicationResponse.header(name: String, value: String) = headers.append(name, value)
fun ApplicationResponse.header(name: String, value: Int) = headers.append(name, value.toString())
fun ApplicationResponse.header(name: String, value: Long) = headers.append(name, value.toString())
fun ApplicationResponse.header(name: String, date: Temporal) = headers.append(name, date.toHttpDateString())

fun ApplicationResponse.sendRedirect(url: String, permanent: Boolean = false): ApplicationRequestStatus {
    headers.append(HttpHeaders.Location, url)
    return send(if (permanent) HttpStatusCode.MovedPermanently else HttpStatusCode.Found)
}

fun ApplicationResponse.sendError(code: HttpStatusCode, message: String = code.description): ApplicationRequestStatus {
    return send(TextErrorContent(code, message))
}

fun ApplicationResponse.sendAuthenticationRequest(realm: String): ApplicationRequestStatus {
    headers.append(HttpHeaders.WWWAuthenticate, "Basic realm=\"$realm\"")
    return send(HttpStatusCode.Unauthorized)
}

public fun ApplicationResponse.sendBytes(bytes: ByteArray): ApplicationRequestStatus {
    status(HttpStatusCode.OK)
    streamBytes(bytes)
    return ApplicationRequestStatus.Handled
}

public fun ApplicationResponse.sendText(contentType: ContentType, text: String): ApplicationRequestStatus {
    return send(TextContent(contentType, text))
}

public fun ApplicationResponse.sendText(text: String): ApplicationRequestStatus {
    return sendText(ContentType.Text.Plain, text)
}


