/*
 * Copyright 2014-2024 JetBrains s.r.o and contributors. Use of this source code is governed by the Apache 2.0 license.
 */

package io.ktor.client.statement

import io.ktor.client.call.*
import io.ktor.utils.io.*
import io.ktor.utils.io.core.*
import kotlinx.coroutines.sync.*
import kotlinx.io.*

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
        /**
         * Create from a streaming source.
         */
        public fun create(channel: ByteReadChannel): HttpResponseBody =
            ChannelResponseBody(BodySource.Stream(channel))

        /**
         * Create from a byte array.  This type of body can be read repeatedly.
         */
        public fun create(bytes: ByteArray): HttpResponseBody =
            ChannelResponseBody(BodySource.ByteArray(bytes))

        public fun empty(): HttpResponseBody =
            ChannelResponseBody(BodySource.Stream(ByteReadChannel.Empty))
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
    public suspend fun <T> read(consume: Boolean = true, operation: suspend ByteReadChannel.() -> T): T

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

}

public fun HttpResponseBody.lazyCopy(): HttpResponseBody =
    LazyCopyResponseBody(this)

private class LazyCopyResponseBody(private val delegate: HttpResponseBody): HttpResponseBody by delegate {

    override suspend fun <T> read(consume: Boolean, operation: suspend ByteReadChannel.() -> T): T =
        delegate.copy().read(consume, operation)

    override fun toChannel(): ByteReadChannel =
        delegate.toChannel()
}

/**
 * Implementation for a response body that allows for copying as needed.
 */
private class ChannelResponseBody(private var source: BodySource): HttpResponseBody {
    private val mutex = Mutex()
    private var consumed: InitialReceiveEvent? = null

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

    override suspend fun <T> read(consume: Boolean, operation: suspend (ByteReadChannel) -> T): T =
        consumed?.let {
            throw DoubleReceiveException(it)
        } ?: with(source) {
            try {
                operation(toChannel())
            } finally {
                if (consume && discard())
                    consumed = InitialReceiveEvent()
            }
        }

    // TODO remove this
    @InternalAPI
    override fun toChannel(): ByteReadChannel =
        source.toChannel()

    override suspend fun discard() {
        source.discard()
    }

    override suspend fun cancel(cause: Throwable?) {
        source.cancel(cause)
    }
}

private sealed interface BodySource {

    /**
     * Reads stream into an in-memory buffer, so it can be re-read.
     *
     * If the source is already static, this returns itself.
     *
     * @return a source that may be read independently from the original stream
     */
    suspend fun toStatic(): BodySource

    /**
     * Delegates discarding to the source, ensuring everything is cleaned up.
     *
     * @return true if the response is consumed
     */
    suspend fun discard(): Boolean

    /**
     * Cancels the underlying source.
     *
     * @param cause the initial cause for cancellation
     */
    suspend fun cancel(cause: Throwable?)

    fun copy(deep: Boolean): BodySource
    fun toChannel(): ByteReadChannel

    class Stream(val channel: ByteReadChannel): BodySource {
        @OptIn(InternalAPI::class)
        override suspend fun toStatic() =
            if (channel.isClosedForRead)
                Buffer(channel.readBuffer)
            else Buffer(channel.readBuffer())

        override fun copy(deep: Boolean): BodySource =
            throw IllegalStateException("Stream body cannot be copied")

        override fun toChannel(): ByteReadChannel = channel

        override suspend fun discard(): Boolean {
            if (!channel.isClosedForRead) {
                runCatching {
                    channel.discard()
                }
            }
            return true
        }

        override suspend fun cancel(cause: Throwable?) {
            channel.cancel(cause)
        }
    }

    class ByteArray(val bytes: kotlin.ByteArray): BodySource {

        override suspend fun toStatic(): BodySource = this

        override fun toChannel(): ByteReadChannel =
            ByteReadChannel(bytes)

        override suspend fun discard() = false

        override suspend fun cancel(cause: Throwable?) {}

        override fun copy(deep: Boolean): BodySource = this
    }

    class Buffer(val source: Source): BodySource {

        override suspend fun toStatic(): Buffer = this

        override fun toChannel(): ByteReadChannel =
            ByteReadChannel(source)

        override suspend fun discard(): Boolean {
            source.discard()
            return true
        }

        override suspend fun cancel(cause: Throwable?) {
            source.close()
        }

        override fun copy(deep: Boolean): BodySource =
            Buffer(
                if (deep) source.copy()
                else source.peek()
            )
    }
}
