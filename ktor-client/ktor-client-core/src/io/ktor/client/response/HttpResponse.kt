package io.ktor.client.response

import io.ktor.client.*
import io.ktor.client.call.*
import io.ktor.compat.*
import io.ktor.http.*
import kotlinx.coroutines.experimental.*
import kotlinx.coroutines.experimental.io.*
import kotlinx.io.charsets.*
import kotlinx.io.core.*

/**
 * A response for [HttpClient], second part of [HttpClientCall].
 */
interface HttpResponse : HttpMessage, Closeable {
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
     * [Date] of the request start.
     */
//    val requestTime: Date

    /**
     * [Date] of the response start.
     */
//    val responseTime: Date

    /**
     * A [Job] representing the process of this response.
     */
    val executionContext: Job

    /**
     * [ByteReadChannel] with the payload of the response.
     */
    val content: ByteReadChannel
}

/**
 * Read the [HttpResponse.content] as a String. You can pass an optional [charset]
 * to specify a charset in the case no one is specified as part of the Content-Type response.
 * If no charset specified either as parameter or as part of the response, [Charsets.ISO_8859_1] will be used.
 *
 * Note that [charset] parameter will be ignored if the response already has a charset.
 *      So it just acts as a fallback, honoring the server preference.
 */
suspend fun HttpResponse.readText(charset: Charset? = null): String {
    val packet = content.readRemaining(Long.MAX_VALUE).readBytes()
    return String(packet, charset = charset() ?: charset ?: Charset.forName("ISO_8859_1"))
}
