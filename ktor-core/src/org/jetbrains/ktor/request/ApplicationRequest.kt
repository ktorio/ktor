package org.jetbrains.ktor.request

import org.jetbrains.ktor.application.*
import org.jetbrains.ktor.features.http.*
import org.jetbrains.ktor.http.*

val ApplicationRequest.uri: String get() = originRoute.uri

/**
 * Returns request HTTP method possibly overridden via header X-Http-Method-Override
 */
val ApplicationRequest.httpMethod: HttpMethod get() = originRoute.method
val ApplicationRequest.httpVersion: String get() = originRoute.version

fun ApplicationRequest.parameter(name: String): String? = parameters[name]
