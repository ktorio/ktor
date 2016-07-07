package org.jetbrains.ktor.request

import org.jetbrains.ktor.application.*
import org.jetbrains.ktor.http.*

val ApplicationRequest.uri: String get() = requestLine.uri
/**
 * Returns request HTTP method possibly overridden via header X-Http-Method-Override
 */
val ApplicationRequest.httpMethod: HttpMethod get() = header(HttpHeaders.XHttpMethodOverride)?.let { HttpMethod.parse(it) } ?: requestLine.method
val ApplicationRequest.httpVersion: String get() = requestLine.version
fun ApplicationRequest.parameter(name: String): String? = parameters[name]
