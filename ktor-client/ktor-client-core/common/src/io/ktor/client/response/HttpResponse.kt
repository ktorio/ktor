/*
 * Copyright 2014-2019 JetBrains s.r.o and contributors. Use of this source code is governed by the Apache 2.0 license.
 */

package io.ktor.client.response

import io.ktor.client.*
import io.ktor.client.call.*
import io.ktor.client.features.*
import io.ktor.http.*
import io.ktor.util.date.*
import kotlinx.atomicfu.*
import kotlinx.coroutines.*
import io.ktor.utils.io.*
import io.ktor.utils.io.charsets.*
import io.ktor.utils.io.core.*

/**
 * A response for [HttpClient], second part of [HttpClientCall].
 */
abstract class HttpResponse : HttpMessage, CoroutineScope, Closeable {
    /**
     * The associated [HttpClientCall] containing both
     * the underlying [HttpClientCall.request] and [HttpClientCall.response].
     */
    abstract val call: HttpClientCall

    /**
     * The [HttpStatusCode] returned by the server. It includes both,
     * the [HttpStatusCode.description] and the [HttpStatusCode.value] (code).
     */
    abstract val status: HttpStatusCode

    /**
     * HTTP version. Usually [HttpProtocolVersion.HTTP_1_1] or [HttpProtocolVersion.HTTP_2_0].
     */
    abstract val version: HttpProtocolVersion

    /**
     * [GMTDate] of the request start.
     */
    abstract val requestTime: GMTDate

    /**
     * [GMTDate] of the response start.
     */
    abstract val responseTime: GMTDate

    /**
     * A [Job] representing the process of this response.
     */
    @Deprecated(
        "Binary compatibility.",
        level = DeprecationLevel.HIDDEN
    )
    val executionContext: Job
        get() = coroutineContext[Job]!!

    /**
     * [ByteReadChannel] with the payload of the response.
     */
    abstract val content: ByteReadChannel

    private val closed: AtomicBoolean = atomic(false)

    @Suppress("KDocMissingDocumentation")
    override fun close() {
        if (!closed.compareAndSet(false, true)) return

        launch {
            content.cancel()
        }
        @Suppress("UNCHECKED_CAST")
        (coroutineContext[Job] as CompletableJob).complete()
    }
}

/**
 * Read the [HttpResponse.content] as a String. You can pass an optional [charset]
 * to specify a charset in the case no one is specified as part of the Content-Type response.
 * If no charset specified either as parameter or as part of the response,
 * [HttpResponseConfig.defaultCharset] will be used.
 *
 * Note that [charset] parameter will be ignored if the response already has a charset.
 *      So it just acts as a fallback, honoring the server preference.
 */
suspend fun HttpResponse.readText(charset: Charset? = null): String {
    val packet = receive<Input>()
    val actualCharset = charset()
        ?: charset
        ?: call.client.feature(HttpPlainText)?.responseCharsetFallback
        ?: Charsets.UTF_8

    return packet.readText(charset = actualCharset)
}
