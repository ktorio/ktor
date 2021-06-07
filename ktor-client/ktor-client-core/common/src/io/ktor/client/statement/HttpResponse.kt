/*
* Copyright 2014-2021 JetBrains s.r.o and contributors. Use of this source code is governed by the Apache 2.0 license.
*/

package io.ktor.client.statement

import io.ktor.client.*
import io.ktor.client.call.*
import io.ktor.client.request.*
import io.ktor.http.*
import io.ktor.util.*
import io.ktor.util.date.*
import io.ktor.utils.io.*
import kotlinx.coroutines.*

/**
 * A response for [HttpClient], second part of [HttpClientCall].
 */
public abstract class HttpResponse : HttpMessage, CoroutineScope {
    /**
     * The associated [HttpClientCall] containing both
     * the underlying [HttpClientCall.request] and [HttpClientCall.response].
     */
    public abstract val call: HttpClientCall

    /**
     * The [HttpStatusCode] returned by the server. It includes both,
     * the [HttpStatusCode.description] and the [HttpStatusCode.value] (code).
     */
    public abstract val status: HttpStatusCode

    /**
     * HTTP version. Usually [HttpProtocolVersion.HTTP_1_1] or [HttpProtocolVersion.HTTP_2_0].
     */
    public abstract val version: HttpProtocolVersion

    /**
     * [GMTDate] of the request start.
     */
    public abstract val requestTime: GMTDate

    /**
     * [GMTDate] of the response start.
     */
    public abstract val responseTime: GMTDate

    /**
     * Unmodified [ByteReadChannel] with the raw payload of the response.
     *
     * **Note:** this content doesn't go through any interceptors from [HttpResponsePipeline].
     * If you need modified content, use [HttpResponse::receive<ByteReadChannel>] function.
     */
    public abstract val content: ByteReadChannel

    override fun toString(): String = "HttpResponse[${request.url}, $status]"
}

/**
 * [HttpRequest] associated with this response.
 */
public val HttpResponse.request: HttpRequest get() = call.request

@Suppress("unused", "KDocMissingDocumentation")
@Deprecated("Close is obsolete for [HttpResponse]", replaceWith = ReplaceWith("this"))
public fun HttpResponse.close() {
}

@Suppress("UNUSED_PARAMETER", "KDocMissingDocumentation", "unused")
@Deprecated("Use is obsolete for [HttpResponse]", replaceWith = ReplaceWith("this.also(block)"))
public fun HttpResponse.use(block: () -> Unit) {
}

@Suppress("unused", "KDocMissingDocumentation")
@Deprecated("[response] is obsolete for [HttpResponse]", replaceWith = ReplaceWith("this"))
public val HttpResponse.response: HttpResponse
    get() = this

@InternalAPI
@PublishedApi
internal fun HttpResponse.complete() {
    val job = coroutineContext[Job]!! as CompletableJob
    job.complete()
}
