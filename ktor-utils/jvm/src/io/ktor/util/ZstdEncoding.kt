/*
 * Copyright 2014-2025 JetBrains s.r.o and contributors. Use of this source code is governed by the Apache 2.0 license.
 */

package io.ktor.util

import com.github.luben.zstd.ZstdInputStream
import com.github.luben.zstd.ZstdOutputStream
import io.ktor.util.cio.KtorDefaultPool
import io.ktor.utils.io.ByteReadChannel
import io.ktor.utils.io.ByteWriteChannel
import io.ktor.utils.io.jvm.javaio.toInputStream
import io.ktor.utils.io.jvm.javaio.toOutputStream
import io.ktor.utils.io.pool.ObjectPool
import io.ktor.utils.io.readAvailable
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
    coroutineContext: CoroutineContext = Dispatchers.Unconfined
): ByteReadChannel = GlobalScope.writer(coroutineContext, autoFlush = true) {
    this@encoded.encodeTo(channel, pool)
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
    val zstdStream = ZstdInputStream(toInputStream())
    val buf = pool.borrow()

    try {
        var decompressedBytesCount: Int
        do {
            decompressedBytesCount = zstdStream.read(buf.array())

            if (decompressedBytesCount > 0) {
                buf.limit(decompressedBytesCount)
                destination.writeFully(buf)
            }
        } while (decompressedBytesCount > 0)
    } finally {
        zstdStream.close()
        pool.recycle(buf)
    }
}

private suspend fun ByteReadChannel.encodeTo(
    destination: ByteWriteChannel,
    pool: ObjectPool<ByteBuffer> = KtorDefaultPool
) {
    val zstdStream = ZstdOutputStream(destination.toOutputStream())
    val buf = pool.borrow()

    try {
        while (!isClosedForRead) {
            buf.clear()
            if (readAvailable(buf) <= 0) continue
            buf.flip()

            zstdStream.write(buf.array(), buf.position(), buf.remaining())
        }
    } finally {
        zstdStream.flush()
        zstdStream.close()
        pool.recycle(buf)
    }
}
