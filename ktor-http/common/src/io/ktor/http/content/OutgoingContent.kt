/*
* Copyright 2014-2021 JetBrains s.r.o and contributors. Use of this source code is governed by the Apache 2.0 license.
*/

package io.ktor.http.content

import io.ktor.http.*
import io.ktor.util.*
import io.ktor.utils.io.*
import kotlinx.coroutines.*
import kotlin.coroutines.*

/**
 * Information about the content to be sent to the peer, recognized by a client or server engine
 */
public sealed class OutgoingContent {
    /**
     * Specifies [ContentType] for this resource.
     */
    public open val contentType: ContentType? get() = null

    /**
     * Specifies content length in bytes for this resource.
     *
     * If null, the resources will be sent as `Transfer-Encoding: chunked`
     */
    public open val contentLength: Long? get() = null

    /**
     * Status code to set when sending this content
     */
    public open val status: HttpStatusCode?
        get() = null

    /**
     * Headers to set when sending this content
     */
    public open val headers: Headers
        get() = Headers.Empty

    private var extensionProperties: Attributes? = null

    /**
     * Gets an extension property for this content
     */
    public open fun <T : Any> getProperty(key: AttributeKey<T>): T? = extensionProperties?.getOrNull(key)

    /**
     * Sets an extension property for this content
     */
    public open fun <T : Any> setProperty(key: AttributeKey<T>, value: T?) {
        when {
            value == null && extensionProperties == null -> return
            value == null -> extensionProperties?.remove(key)
            else -> (extensionProperties ?: Attributes()).also { extensionProperties = it }.put(key, value)
        }
    }

    /**
     * Variant of a [OutgoingContent] without a payload
     */
    public abstract class NoContent : OutgoingContent()

    /**
     * Variant of a [OutgoingContent] with payload read from [ByteReadChannel]
     *
     */
    public abstract class ReadChannelContent : OutgoingContent() {
        /**
         * Provides [ByteReadChannel] for the content
         */
        public abstract fun readFrom(): ByteReadChannel

        /**
         * Provides [ByteReadChannel] for the given range of the content
         */
        public open fun readFrom(range: LongRange): ByteReadChannel = if (range.isEmpty()) {
            ByteReadChannel.Empty
        } else {
            GlobalScope.writer(Dispatchers.Unconfined, autoFlush = true) {
                val source = readFrom()
                source.discard(range.start)
                val limit = range.endInclusive - range.start + 1
                source.copyTo(channel, limit)
            }.channel
        }
    }

    /**
     * Variant of a [OutgoingContent] with payload written to [ByteWriteChannel]
     */
    public abstract class WriteChannelContent : OutgoingContent() {
        /**
         * Receives [channel] provided by the engine and writes all data to it
         */
        public abstract suspend fun writeTo(channel: ByteWriteChannel)
    }

    /**
     * Variant of a [OutgoingContent] with payload represented as [ByteArray]
     */
    public abstract class ByteArrayContent : OutgoingContent() {
        /**
         * Provides [ByteArray] which engine will send to peer
         */
        public abstract fun bytes(): ByteArray
    }

    /**
     * Variant of a [OutgoingContent] for upgrading an HTTP connection
     */
    public abstract class ProtocolUpgrade : OutgoingContent() {
        final override val status: HttpStatusCode?
            get() = HttpStatusCode.SwitchingProtocols

        /**
         * Upgrades an HTTP connection
         * @param input is a [ByteReadChannel] for an upgraded connection
         * @param output is a [ByteWriteChannel] for an upgraded connection
         * @param engineContext is a [CoroutineContext] to execute non-blocking code, such as parsing or processing
         * @param userContext is a [CoroutineContext] to execute user-provided callbacks or code potentially blocking
         */
        public abstract suspend fun upgrade(
            input: ByteReadChannel,
            output: ByteWriteChannel,
            engineContext: CoroutineContext,
            userContext: CoroutineContext
        ): Job
    }
}
