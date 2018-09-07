package io.ktor.http

import io.ktor.application.*
import io.ktor.response.*
import io.ktor.util.*

/**
 * Produces HTTP/2 push from server to client or sets HTTP/1.x hint header
 * or does nothing.
 * Exact behaviour is up to engine implementation.
 */
fun ApplicationCall.push(pathAndQuery: String) {
    val (path, query) = pathAndQuery.chomp("?") { pathAndQuery to "" }
    push(path, parseQueryString(query))
}

/**
 * Produces HTTP/2 push from server to client or sets HTTP/1.x hint header
 * or does nothing.
 * Exact behaviour is up to engine implementation.
 */
fun ApplicationCall.push(encodedPath: String, parameters: Parameters) {
    push {
        url.encodedPath = encodedPath
        url.parameters.clear()
        url.parameters.appendAll(parameters)
    }
}

/**
 * Produces HTTP/2 push from server to client or sets HTTP/1.x hint header
 * or does nothing (may call or not call [block]).
 * Exact behaviour is up to engine implementation.
 */
@KtorExperimentalAPI
fun ApplicationCall.push(block: ResponsePushBuilder.() -> Unit) {
    response.push(DefaultResponsePushBuilder(this).apply(block))
}
