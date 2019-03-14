package io.ktor.client.response

import io.ktor.client.*
import io.ktor.client.call.*
import io.ktor.client.features.*
import io.ktor.http.*
import io.ktor.util.date.*
import kotlinx.coroutines.*
import kotlinx.coroutines.io.*
import kotlinx.io.charsets.*
import kotlinx.io.core.*

/**
 * A response for [HttpClient], second part of [HttpClientCall].
 */
interface HttpResponse : HttpMessage, CoroutineScope, Closeable {
    /**
     * The associated [HttpClientCall] containing both
     * the underlying [HttpClientCall.request] and [HttpClientCall.response].
     */
    val call: HttpClientCall

    /**
     * The [HttpStatusCode] returned by the server. It includes both,
     * the [HttpStatusCode.description] and the [HttpStatusCode.value] (code).
     */
    val status: HttpStatusCode

    /**
     * HTTP version. Usually [HttpProtocolVersion.HTTP_1_1] or [HttpProtocolVersion.HTTP_2_0].
     */
    val version: HttpProtocolVersion

    /**
     * [GMTDate] of the request start.
     */
    val requestTime: GMTDate

    /**
     * [GMTDate] of the response start.
     */
    val responseTime: GMTDate

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
    val content: ByteReadChannel

    @Suppress("KDocMissingDocumentation")
    override fun close() {
        launch {
            content.discard()
        }
        @Suppress("UNCHECKED_CAST")
        (coroutineContext[Job] as CompletableDeferred<Unit>).complete(Unit)
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
    val packet = receive<ByteReadPacket>()
    val actualCharset = charset() ?: charset ?: call.client.feature(HttpPlainText)!!.responseCharsetFallback
    return packet.readText(charset = actualCharset)
}
