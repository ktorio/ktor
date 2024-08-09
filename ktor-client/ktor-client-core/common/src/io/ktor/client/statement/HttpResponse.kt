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
import io.ktor.utils.io.charsets.*
import io.ktor.utils.io.core.*
import kotlinx.coroutines.*
import kotlinx.coroutines.sync.*
import kotlinx.io.*
import kotlinx.io.Buffer

/**
 * An [HttpClient]'s response, a second part of [HttpClientCall].
 *
 * Learn more from [Receiving responses](https://ktor.io/docs/response.html).
 */
public interface HttpResponse : HttpMessage, CoroutineScope {
    /**
     * The associated [HttpClientCall] containing both
     * the underlying [HttpClientCall.request] and [HttpClientCall.response].
     */
    public val call: HttpClientCall

    /**
     * The [HttpStatusCode] returned by the server. It includes both,
     * the [HttpStatusCode.description] and the [HttpStatusCode.value] (code).
     */
    public val status: HttpStatusCode

    /**
     * HTTP version. Usually [HttpProtocolVersion.HTTP_1_1] or [HttpProtocolVersion.HTTP_2_0].
     */
    public val version: HttpProtocolVersion

    /**
     * [GMTDate] of the request start.
     */
    public val requestTime: GMTDate

    /**
     * [GMTDate] of the response start.
     */
    public val responseTime: GMTDate

    /**
     * Unmodified [ByteReadChannel] with the raw payload of the response.
     *
     * **Note:** this content doesn't go through any interceptors from [HttpResponsePipeline].
     * If you need the modified content, use the [bodyChannel] function.
     */
    @InternalAPI
    public val body: HttpResponseBody
}

/**
 * Interface for referencing HttpResponse body.
 *
 * Can either be consumed using `read()` function, or copied for repeated reads.
 *
 * When copied, the original stream from the socket is consumed and the body reference is
 * replaced with the original bytes.
 */
public interface HttpResponseBody {
    public companion object {
        public fun create(channel: ByteReadChannel): HttpResponseBody =
            ChannelResponseBody(BodySource.Stream(channel))

        public fun create(bytes: ByteArray): HttpResponseBody =
            ChannelResponseBody(BodySource.Static(Buffer().apply { write(bytes) }))

        public fun empty(): HttpResponseBody =
            ChannelResponseBody(BodySource.Empty)

        public fun repeatable(bytes: Source): HttpResponseBody =
            RepeatableResponseBody(BodySource.Static(bytes))

    }

    /**
     * Returns a copy of the body while ensuring the original bytes remain.
     *
     * By default, this is a shallow copy, which points to the same buffer and will encounter an IllegalStateException
     * if the original is consumed before the copy.
     *
     * @param deep this will copy the contents of the buffer so that it may be consumed independently main body.
     */
    public suspend fun copy(deep: Boolean = false): HttpResponseBody

    /**
     * Consumes the body with the given operation on the byte channel.
     */
    public suspend fun <T> read(operation: suspend ByteReadChannel.() -> T): T

    /**
     * Get the backing ByteReadChannel
     */
    public fun toChannel(): ByteReadChannel

    /**
     * Discards the remainder of the buffer.
     */
    public suspend fun discard()

    /**
     * Disconnects from the source.
     */
    public suspend fun cancel(cause: Throwable? = null)

    /**
     * Implementation for a response body that allows for copying as needed.
     */
    private class ChannelResponseBody(private var source: BodySource): HttpResponseBody {
        private val mutex = Mutex()

        /**
         * Reads in the response to a buffer, then replaces the current source and returns a new pointer.
         */
        override suspend fun copy(deep: Boolean): HttpResponseBody =
            mutex.withLock {
                source.toStatic().let { buffer ->
                    source = buffer
                    ChannelResponseBody(buffer.copy(deep))
                }
            }

        override suspend fun <T> read(operation: suspend (ByteReadChannel) -> T): T =
            mutex.withLock {
                source.toChannel().let { channel ->
                    try {
                        operation(channel)
                    } finally {
                        source.discard()
                    }
                }
            }

        override fun toChannel(): ByteReadChannel =
            source.toChannel()

        override suspend fun discard() {
            source.discard()
        }

        override suspend fun cancel(cause: Throwable?) {
            source.cancel(cause)
        }
    }

    /**
     * Implementation that can be read repeatedly.
     *
     * This is used for the SaveBodyPlugin.
     *
     * It is recommended to instead use `body.copy()` when inspecting the body without consuming.
     */
    private class RepeatableResponseBody(private var buffer: BodySource.Static): HttpResponseBody {
        /**
         * Copies are not reusable.
         */
        override suspend fun copy(deep: Boolean): HttpResponseBody =
            ChannelResponseBody(buffer.copy(deep))

        /**
         * Read always operates on a new pointer to the buffer, never consuming.
         */
        override suspend fun <T> read(operation: suspend ByteReadChannel.() -> T): T =
            buffer.copy(deep = false).toChannel().let { channel ->
                operation(channel)
            }

        override fun toChannel(): ByteReadChannel =
            buffer.copy(deep = false).toChannel()

        /**
         * Explicit calls to discard will actually consume the buffer.
         */
        override suspend fun discard() {
            buffer.discard()
        }

        /**
         * Explicit calls to cancel will actually close the buffer.
         */
        override suspend fun cancel(cause: Throwable?) {
            buffer.cancel(cause)
        }
    }

    private sealed interface BodySource {

        suspend fun toStatic(): BodySource
        suspend fun discard()
        suspend fun cancel(cause: Throwable?)

        fun copy(deep: Boolean): BodySource
        fun toChannel(): ByteReadChannel

        object Empty: BodySource {
            override suspend fun toStatic(): BodySource = this
            override fun copy(deep: Boolean): BodySource = this
            override fun toChannel(): ByteReadChannel =
                ByteReadChannel.Empty
            override suspend fun discard() {}
            override suspend fun cancel(cause: Throwable?) {}
        }

        class Stream(val channel: ByteReadChannel): BodySource {
            override suspend fun toStatic() =
                if (channel.isClosedForRead) Empty
                else Static(channel.readBuffer())

            override fun copy(deep: Boolean): BodySource =
                throw IllegalStateException("Stream body cannot be copied")

            override fun toChannel(): ByteReadChannel = channel

            override suspend fun discard() {
                if (!channel.isClosedForRead) {
                    runCatching {
                        channel.discard()
                    }
                }
            }

            override suspend fun cancel(cause: Throwable?) {
                channel.cancel(cause)
            }
        }

        class Static(val source: Source): BodySource {

            override suspend fun toStatic(): Static = this

            override fun toChannel(): ByteReadChannel =
                ByteReadChannel(source)

            override suspend fun discard() {
                source.discard()
            }

            override suspend fun cancel(cause: Throwable?) {
                source.close()
            }

            override fun copy(deep: Boolean): BodySource =
                Static(if (deep) source.copy() else source.peek())
        }
    }
}

public suspend fun HttpResponseBody.readText(charset: Charset = Charsets.UTF_8, max: Int = Int.MAX_VALUE): String =
    read { readRemaining().readText(charset, max) }

public suspend fun HttpResponseBody.readBytes(): ByteArray =
    read { readRemaining().readByteArray() }

public suspend fun HttpResponseBody.readBytes(count: Int): ByteArray =
    ByteArray(count).also {
        read { readFully(it) }
    }

/**
 * Gets [HttpRequest] associated with this response.
 */
public val HttpResponse.request: HttpRequest get() = call.request

@InternalAPI
@PublishedApi
internal fun HttpResponse.complete() {
    val job = coroutineContext[Job]!! as CompletableJob
    job.complete()
}

/**
 * Reads the [HttpResponse.body] as a String. You can pass an optional [charset]
 * to specify a charset in the case no one is specified as part of the `Content-Type` response.
 * If no charset specified either as parameter or as part of the response,
 * [io.ktor.client.plugins.HttpPlainText] settings will be used.
 *
 * Note that [fallbackCharset] parameter will be ignored if the response already has a charset.
 *      So it just acts as a fallback, honoring the server preference.
 */

public suspend fun HttpResponse.bodyAsText(fallbackCharset: Charset = Charsets.UTF_8): String {
    val originCharset = charset() ?: fallbackCharset
    val decoder = originCharset.newDecoder()
    val input = body<Source>()

    return decoder.decode(input)
}

/**
 * Reads the [HttpResponse.body] as a [ByteReadChannel].
 */
public suspend fun HttpResponse.bodyAsChannel(): ByteReadChannel = body()
