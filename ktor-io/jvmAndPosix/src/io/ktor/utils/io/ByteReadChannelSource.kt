/*
 * Copyright 2014-2025 JetBrains s.r.o and contributors. Use of this source code is governed by the Apache 2.0 license.
 */

package io.ktor.utils.io

import kotlinx.coroutines.runBlocking
import kotlinx.io.Buffer
import kotlinx.io.RawSource

/**
 * Converts the current `ByteReadChannel` instance into a `RawSource`.
 * This enables the usage of a `ByteReadChannel` as a `RawSource` for reading operations.
 *
 * Please note: the [RawSource] produced by this operation uses [runBlocking] to wait for the content to be available.
 *
 *
 * [Report a problem](https://ktor.io/feedback/?fqname=io.ktor.utils.io.asSource)
 *
 * @return a `RawSource` implementation that wraps the `ByteReadChannel`.
 */
public fun ByteReadChannel.asSource(): RawSource = ByteReadChannelSource(this)

internal class ByteReadChannelSource(private val origin: ByteReadChannel) : RawSource {

    @OptIn(InternalAPI::class)
    override fun readAtMostTo(sink: Buffer, byteCount: Long): Long {
        if (origin.readBuffer.exhausted()) {
            runBlocking { origin.awaitContent() }
        }

        if (origin.readBuffer.exhausted()) {
            return -1
        }

        return origin.readBuffer.readAtMostTo(sink, byteCount)
    }

    override fun close() {
        origin.cancel()
    }
}
