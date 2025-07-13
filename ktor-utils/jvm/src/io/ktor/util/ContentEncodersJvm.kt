/*
 * Copyright 2014-2023 JetBrains s.r.o and contributors. Use of this source code is governed by the Apache 2.0 license.
 */

package io.ktor.util

import io.ktor.utils.io.ByteReadChannel
import io.ktor.utils.io.ByteWriteChannel
import kotlin.coroutines.CoroutineContext

/**
 * Implementation of [ContentEncoder] using gzip algorithm
 *
 * [Report a problem](https://ktor.io/feedback/?fqname=io.ktor.util.GZipEncoder)
 */
public actual object GZipEncoder : ContentEncoder, Encoder by GZip {
    actual override val name: String = "gzip"
}

/**
 * Implementation of [ContentEncoder] using deflate algorithm
 *
 * [Report a problem](https://ktor.io/feedback/?fqname=io.ktor.util.DeflateEncoder)
 */
public actual object DeflateEncoder : ContentEncoder, Encoder by Deflate {
    actual override val name: String = "deflate"
}

/**
 * Implementation of [ContentEncoder] using zstd algorithm
 *
 * [Report a problem](https://ktor.io/feedback/?fqname=io.ktor.util.ZstdEncoder)
 */
public actual object ZstdEncoder : ContentEncoder, Encoder {
    actual override val name: String = "zstd"
    public actual var compressionLevel: Int = 3
    private var encoder: Encoder = Zstd(compressionLevel)

    actual override fun encode(
        source: ByteReadChannel,
        coroutineContext: CoroutineContext
    ): ByteReadChannel = encoder.encode(source, coroutineContext)

    actual override fun encode(
        source: ByteWriteChannel,
        coroutineContext: CoroutineContext
    ): ByteWriteChannel = encoder.encode(source, coroutineContext)

    actual override fun decode(
        source: ByteReadChannel,
        coroutineContext: CoroutineContext
    ): ByteReadChannel = encoder.decode(source, coroutineContext)
}
