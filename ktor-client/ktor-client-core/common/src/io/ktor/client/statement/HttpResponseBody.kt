/*
 * Copyright 2014-2024 JetBrains s.r.o and contributors. Use of this source code is governed by the Apache 2.0 license.
 */

package io.ktor.client.statement

import io.ktor.utils.io.*
import io.ktor.utils.io.core.*
import kotlinx.coroutines.sync.*
import kotlinx.io.*
import kotlinx.io.Buffer

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
            ChannelResponseBody(BodySource.Stream(ByteReadChannel.Empty))

        public fun repeatable(bytes: Buffer): HttpResponseBody =
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

}

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
 * Implementation that can be read multiple times.
 *
 * This is used for the SaveBodyPlugin.
 *
 * It is better to instead use `body.copy()` when inspecting the body without consuming, because then
 * buffer references are cleaned up after the copied body is consumed.
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

    /**
     * Reads stream into an in-memory buffer, so it can be re-read.
     *
     * If the source is already static, this returns itself.
     */
    suspend fun toStatic(): BodySource

    /**
     * Delegates discarding to the source, ensuring everything is cleaned up.
     */
    suspend fun discard()

    /**
     * Cancels the underlying source.
     */
    suspend fun cancel(cause: Throwable?)

    fun copy(deep: Boolean): BodySource
    fun toChannel(): ByteReadChannel

    class Stream(val channel: ByteReadChannel): BodySource {
        @OptIn(InternalAPI::class)
        override suspend fun toStatic() =
            if (channel.isClosedForRead)
                Static(channel.readBuffer)
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
            Static(
                if (deep) source.copy()
                else source.peek()
            )
    }
}
