package io.ktor.util

import io.ktor.application.*
import io.ktor.features.*
import io.ktor.http.*
import io.ktor.request.*


fun URLBuilder.Companion.createFromCall(call: ApplicationCall): URLBuilder {
    val origin = call.request.origin

    val builder = URLBuilder()
    builder.protocol = URLProtocol.byName[origin.scheme] ?: URLProtocol(origin.scheme, 0)
    builder.host = origin.host.substringBefore(":")
    builder.port = origin.port
    builder.encodedPath = call.request.path()
    builder.parameters.appendAll(call.request.queryParameters)

    return builder
}

fun url(block: URLBuilder.() -> Unit) = URLBuilder().apply(block).buildString()
fun ApplicationCall.url(block: URLBuilder.() -> Unit = {}) =
        URLBuilder.Companion.createFromCall(this).apply(block).buildString()
