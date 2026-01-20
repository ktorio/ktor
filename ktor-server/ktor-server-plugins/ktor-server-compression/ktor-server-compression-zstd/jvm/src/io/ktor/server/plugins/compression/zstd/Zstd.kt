/*
 * Copyright 2014-2026 JetBrains s.r.o and contributors. Use of this source code is governed by the Apache 2.0 license.
 */

package io.ktor.server.plugins.compression.zstd

import io.ktor.encoding.zstd.*
import io.ktor.server.plugins.compression.*
import io.ktor.utils.io.*

/**
 * Appends the 'zstd' encoder with [block] configuration.
 *
 * [Report a problem](https://ktor.io/feedback/?fqname=io.ktor.server.plugins.compression.zstd.zstd)
 *
 * @param level compression level, defaults to [ZstdEncoder.DEFAULT_COMPRESSION_LEVEL]
 */
@OptIn(InternalAPI::class)
public fun CompressionConfig.zstd(
    level: Int = ZstdEncoder.DEFAULT_COMPRESSION_LEVEL,
    block: CompressionEncoderBuilder.() -> Unit = {}
) {
    encoder(ZstdEncoder(level), block)
}

/**
 * Configure compression to use all algorithms, including 'zstd' with the provided compression level.
 *
 * [Report a problem](https://ktor.io/feedback/?fqname=io.ktor.server.plugins.compression.zstd.zstdStandard)
 *
 * @param level compression level, defaults to [ZstdEncoder.DEFAULT_COMPRESSION_LEVEL]
 */
public fun CompressionConfig.zstdStandard(level: Int = ZstdEncoder.DEFAULT_COMPRESSION_LEVEL) {
    gzip()
    deflate()
    zstd(level)
    identity()
}
