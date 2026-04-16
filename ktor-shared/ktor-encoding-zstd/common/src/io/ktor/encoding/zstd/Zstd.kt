/*
 * Copyright 2014-2026 JetBrains s.r.o and contributors. Use of this source code is governed by the Apache 2.0 license.
 */

package io.ktor.encoding.zstd

import io.ktor.util.ContentEncoder
import io.ktor.utils.io.ByteReadChannel
import io.ktor.utils.io.ByteWriteChannel
import kotlin.coroutines.CoroutineContext

/**
 * Implementation of [ContentEncoder] using zstd algorithm
 *
 * [Report a problem](https://ktor.io/feedback/?fqname=io.ktor.encoding.zstd.ZstdEncoder)
 */
public expect class ZstdEncoder : ContentEncoder {
    override val name: String

    override fun encode(
        source: ByteReadChannel,
        coroutineContext: CoroutineContext
    ): ByteReadChannel

    override fun encode(
        source: ByteWriteChannel,
        coroutineContext: CoroutineContext
    ): ByteWriteChannel

    override fun decode(
        source: ByteReadChannel,
        coroutineContext: CoroutineContext
    ): ByteReadChannel
}
