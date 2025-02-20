/*
 * Copyright 2014-2025 JetBrains s.r.o and contributors. Use of this source code is governed by the Apache 2.0 license.
 */

package io.ktor.utils.io

import io.ktor.utils.io.core.*
import kotlinx.coroutines.runBlocking
import kotlinx.io.Buffer
import kotlinx.io.RawSink

/**
 * Converts the current `ByteWriteChannel` instance into a `RawSink`.
 *
 * Please note: the [RawSink] produced by this operation uses [runBlocking] to flush the content.
 *
 * [Report a problem](https://ktor.io/feedback/?fqname=io.ktor.utils.io.asSink)
 */
public fun ByteWriteChannel.asSink(): RawSink = ByteWriteChannelSink(this)

internal class ByteWriteChannelSink(private val origin: ByteWriteChannel) : RawSink {

    @OptIn(InternalAPI::class)
    override fun write(source: Buffer, byteCount: Long) {
        origin.rethrowCloseCauseIfNeeded()

        origin.writeBuffer.write(source, byteCount)

        if ((origin as? ByteChannel)?.autoFlush == true || origin.writeBuffer.size >= CHANNEL_MAX_SIZE) {
            runBlocking {
                flush()
            }
        }
    }

    @OptIn(InternalAPI::class)
    override fun flush() = runBlocking {
        origin.rethrowCloseCauseIfNeeded()

        origin.flush()
    }

    @OptIn(InternalAPI::class)
    override fun close() = runBlocking {
        origin.rethrowCloseCauseIfNeeded()

        origin.flushAndClose()
    }
}
