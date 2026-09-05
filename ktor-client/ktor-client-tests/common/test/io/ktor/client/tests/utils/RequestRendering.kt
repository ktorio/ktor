/*
 * Copyright 2014-2026 JetBrains s.r.o and contributors. Use of this source code is governed by the Apache 2.0 license.
 */

package io.ktor.client.tests.utils

import io.ktor.client.*
import io.ktor.client.engine.mock.*
import io.ktor.client.request.*
import io.ktor.http.content.*
import io.ktor.utils.io.*

/** Posts a request built by [block] and returns the body the engine was asked to send. */
internal suspend fun renderBody(block: HttpRequestBuilder.() -> Unit): OutgoingContent {
    lateinit var content: OutgoingContent
    val client = HttpClient(MockEngine) {
        engine {
            addHandler { request ->
                content = request.body
                respondOk()
            }
        }
    }
    client.post("http://host/path", block)
    return content
}

/** Posts a request built by [block] and returns the headers the engine was asked to send. */
@OptIn(InternalAPI::class)
internal suspend fun renderHeaders(block: HttpRequestBuilder.() -> Unit): List<Pair<String, String>> {
    val sentHeaders = mutableListOf<Pair<String, String>>()
    val client = HttpClient(MockEngine) {
        engine {
            addHandler { request ->
                request.forEachHeader { key, value -> sentHeaders += key to value }
                respondOk()
            }
        }
    }
    client.post("http://host/path", block)
    return sentHeaders
}
