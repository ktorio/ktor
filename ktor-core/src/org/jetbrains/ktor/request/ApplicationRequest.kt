package org.jetbrains.ktor.request

import org.jetbrains.ktor.application.*
import org.jetbrains.ktor.features.*
import org.jetbrains.ktor.http.*

val ApplicationRequest.uri: String get() = origin.uri

/**
 * Returns request HTTP method possibly overridden via header X-Http-Method-Override
 */
val ApplicationRequest.httpMethod: HttpMethod get() = origin.method
val ApplicationRequest.httpVersion: String get() = origin.version

@Deprecated("Use ApplicationCall.parameters or queryParameters instead")
fun ApplicationRequest.parameter(name: String): String? = parameters[name]
