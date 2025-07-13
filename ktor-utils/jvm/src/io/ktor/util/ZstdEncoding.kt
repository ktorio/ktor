/*
 * Copyright 2014-2025 JetBrains s.r.o and contributors. Use of this source code is governed by the Apache 2.0 license.
 */

package io.ktor.util

import com.github.luben.zstd.ZstdCompressCtx
import com.github.luben.zstd.ZstdDecompressCtx
import io.ktor.util.cio.KtorDefaultPool
import io.ktor.utils.io.ByteReadChannel
import io.ktor.utils.io.ByteWriteChannel
import io.ktor.utils.io.pool.ObjectPool
import io.ktor.utils.io.readAvailable
import io.ktor.utils.io.reader
import io.ktor.utils.io.writeFully
import io.ktor.utils.io.writer
import kotlinx.coroutines.DelicateCoroutinesApi
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.GlobalScope
import java.nio.ByteBuffer
import kotlin.coroutines.CoroutineContext

/**
 * Launch a coroutine on [coroutineContext] that does zstd compression.
 *
 * [Report a problem](https://ktor.io/feedback/?fqname=io.ktor.util.encoded)
 */
@OptIn(DelicateCoroutinesApi::class)
public fun ByteReadChannel.encoded(
    pool: ObjectPool<ByteBuffer> = KtorDefaultPool,
    coroutineContext: CoroutineContext = Dispatchers.Unconfined,
    compressionLevel: Int,
): ByteReadChannel = GlobalScope.writer(coroutineContext, autoFlush = true) {
    this@encoded.encodeTo(channel, pool, compressionLevel)
}.channel

/**
 * Launch a coroutine on [coroutineContext] that does zstd compression.
 *
 * [Report a problem](https://ktor.io/feedback/?fqname=io.ktor.util.encoded)
 */
@OptIn(DelicateCoroutinesApi::class)
public fun ByteWriteChannel.encoded(
    pool: ObjectPool<ByteBuffer> = KtorDefaultPool,
    coroutineContext: CoroutineContext = Dispatchers.Unconfined,
    compressionLevel: Int,
): ByteWriteChannel = GlobalScope.reader(coroutineContext, autoFlush = true) {
    channel.encodeTo(this@encoded, pool, compressionLevel)
}.channel

/**
 * Launch a coroutine on [coroutineContext] that does zstd decompression.
 *
 * [Report a problem](https://ktor.io/feedback/?fqname=io.ktor.util.decoded)
 */
@OptIn(DelicateCoroutinesApi::class)
public fun ByteReadChannel.decoded(
    pool: ObjectPool<ByteBuffer> = KtorDefaultPool,
    coroutineContext: CoroutineContext = Dispatchers.Unconfined
): ByteReadChannel = GlobalScope.writer(coroutineContext) {
    this@decoded.decodeTo(channel, pool)
}.channel

private suspend fun ByteReadChannel.decodeTo(
    destination: ByteWriteChannel,
    pool: ObjectPool<ByteBuffer> = KtorDefaultPool
) {
    val inputBuf = pool.borrow()
    val outputBuf = pool.borrow()
    val ctx = ZstdDecompressCtx()

    try {
        while (!isClosedForRead) {
            inputBuf.clear()
            val bytesRead = readAvailable(inputBuf)
            if (bytesRead <= 0) continue

            val decompressedSize = ctx.decompressByteArray(
                outputBuf.array(), 0, outputBuf.capacity(),
                inputBuf.array(), 0, bytesRead
            )

            destination.writeFully(outputBuf.array(), 0, decompressedSize)
        }
    } finally {
        ctx.close()
        pool.recycle(inputBuf)
        pool.recycle(outputBuf)
    }
}

private suspend fun ByteReadChannel.encodeTo(
    destination: ByteWriteChannel,
    pool: ObjectPool<ByteBuffer> = KtorDefaultPool,
    compressionLevel: Int,
) {
    val inputBuf = pool.borrow()
    val outputBuf = pool.borrow();
    val ctx = ZstdCompressCtx().apply { setLevel(compressionLevel) }

    try {
        while (!isClosedForRead) {
            inputBuf.clear()
            val bytesRead = readAvailable(inputBuf)
            if (bytesRead <= 0) continue

            val compressedSize = ctx.compressByteArray(
                outputBuf.array(), 0, outputBuf.capacity(),
                inputBuf.array(), 0, bytesRead
            )

            destination.writeFully(outputBuf.array(), 0, compressedSize)
        }
    } finally {
        ctx.close()
        pool.recycle(inputBuf)
        pool.recycle(outputBuf)
    }
}
