/*
* Copyright 2014-2021 JetBrains s.r.o and contributors. Use of this source code is governed by the Apache 2.0 license.
*/

package io.ktor.http

import io.ktor.application.*
import io.ktor.response.*
import io.ktor.util.*

/**
 * Produces HTTP/2 push from server to client or sets HTTP/1.x hint header
 * or does nothing.
 * Exact behaviour is up to engine implementation.
 */
@UseHttp2Push
public fun ApplicationCall.push(pathAndQuery: String) {
    val (path, query) = pathAndQuery.chomp("?") { pathAndQuery to "" }
    push(path, parseQueryString(query, decode = false))
}

/**
 * Produces HTTP/2 push from server to client or sets HTTP/1.x hint header
 * or does nothing.
 * Exact behaviour is up to engine implementation.
 */
@UseHttp2Push
public fun ApplicationCall.push(encodedPath: String, encodedParameters: Parameters) {
    push {
        url.encodedPath = encodedPath
        url.encodedParameters.clear()
        url.encodedParameters.appendAll(encodedParameters)
    }
}

/**
 * Produces HTTP/2 push from server to client or sets HTTP/1.x hint header
 * or does nothing (may call or not call [block]).
 * Exact behaviour is up to engine implementation.
 */
@UseHttp2Push
public fun ApplicationCall.push(block: ResponsePushBuilder.() -> Unit) {
    response.push(DefaultResponsePushBuilder(this).apply(block))
}
