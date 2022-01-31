/*
 * Copyright 2014-2022 JetBrains s.r.o and contributors. Use of this source code is governed by the Apache 2.0 license.
 */

package io.ktor.server.plugins.compression

import io.ktor.util.*
import io.ktor.utils.io.*
import kotlinx.coroutines.*
import kotlin.coroutines.*

/**
 * Represents a Compression encoder
 */
public interface CompressionEncoder {
    /**
     * May predict compressed length based on the [originalLength] or return `null` if it is impossible.
     */
    public fun predictCompressedLength(originalLength: Long): Long? = null

    /**
     * Wraps [readChannel] into a compressing [ByteReadChannel]
     */
    public fun compress(
        readChannel: ByteReadChannel,
        coroutineContext: CoroutineContext = Dispatchers.Unconfined
    ): ByteReadChannel

    /**
     * Wraps [writeChannel] into a compressing [ByteWriteChannel]
     */
    public fun compress(
        writeChannel: ByteWriteChannel,
        coroutineContext: CoroutineContext = Dispatchers.Unconfined
    ): ByteWriteChannel
}

/**
 * Implementation of the gzip encoder
 */
public object GzipEncoder : CompressionEncoder {
    override fun compress(readChannel: ByteReadChannel, coroutineContext: CoroutineContext): ByteReadChannel =
        readChannel.deflated(true, coroutineContext = coroutineContext)

    override fun compress(writeChannel: ByteWriteChannel, coroutineContext: CoroutineContext): ByteWriteChannel =
        writeChannel.deflated(true, coroutineContext = coroutineContext)
}

/**
 * Implementation of the deflate encoder
 */
public object DeflateEncoder : CompressionEncoder {
    override fun compress(readChannel: ByteReadChannel, coroutineContext: CoroutineContext): ByteReadChannel =
        readChannel.deflated(false, coroutineContext = coroutineContext)

    override fun compress(writeChannel: ByteWriteChannel, coroutineContext: CoroutineContext): ByteWriteChannel =
        writeChannel.deflated(false, coroutineContext = coroutineContext)
}

/**
 *  Implementation of the identity encoder
 */
public object IdentityEncoder : CompressionEncoder {
    override fun predictCompressedLength(originalLength: Long): Long = originalLength

    override fun compress(
        readChannel: ByteReadChannel,
        coroutineContext: CoroutineContext
    ): ByteReadChannel = readChannel

    override fun compress(
        writeChannel: ByteWriteChannel,
        coroutineContext: CoroutineContext
    ): ByteWriteChannel = writeChannel
}
