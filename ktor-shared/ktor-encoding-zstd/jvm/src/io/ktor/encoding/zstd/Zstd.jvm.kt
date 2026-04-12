/*
 * Copyright 2014-2026 JetBrains s.r.o and contributors. Use of this source code is governed by the Apache 2.0 license.
 */

package io.ktor.encoding.zstd

import com.github.luben.zstd.ZstdCompressCtx
import com.github.luben.zstd.ZstdDecompressCtx
import io.ktor.util.*
import io.ktor.util.cio.*
import io.ktor.utils.io.*
import io.ktor.utils.io.pool.*
import kotlinx.coroutines.DelicateCoroutinesApi
import kotlinx.coroutines.GlobalScope
import java.nio.ByteBuffer
import kotlin.coroutines.CoroutineContext

/**
 * Implementation of [ContentEncoder] using zstd algorithm
 *
 * [Report a problem](https://ktor.io/feedback/?fqname=io.ktor.encoding.zstd.ZstdEncoder)
 */
public actual class ZstdEncoder(
    compressionLevel: Int = DEFAULT_COMPRESSION_LEVEL
) : ContentEncoder, Encoder by Zstd(compressionLevel) {
    public companion object {
        public const val DEFAULT_COMPRESSION_LEVEL: Int = 3
    }

    actual override val name: String = "zstd"
}

/**
 * Implementation of Zstd [Encoder].
 *
 * [Report a problem](https://ktor.io/feedback/?fqname=io.ktor.encoding.zstd.Zstd)
 */
public class Zstd(private val compressionLevel: Int) : Encoder {

    @OptIn(DelicateCoroutinesApi::class)
    override fun encode(source: ByteReadChannel, coroutineContext: CoroutineContext): ByteReadChannel =
        GlobalScope.writer(coroutineContext, autoFlush = true) {
            source.encodeTo(channel, KtorDefaultDirectPool, compressionLevel)
        }.channel

    @OptIn(DelicateCoroutinesApi::class)
    override fun encode(source: ByteWriteChannel, coroutineContext: CoroutineContext): ByteWriteChannel =
        GlobalScope.reader(coroutineContext, autoFlush = true) {
            channel.encodeTo(source, KtorDefaultDirectPool, compressionLevel)
        }.channel

    @OptIn(DelicateCoroutinesApi::class)
    override fun decode(
        source: ByteReadChannel,
        coroutineContext: CoroutineContext
    ): ByteReadChannel = GlobalScope.writer(coroutineContext) {
        source.decodeTo(channel, KtorDefaultDirectPool)
    }.channel

    internal suspend fun ByteReadChannel.decodeTo(
        destination: ByteWriteChannel,
        pool: ObjectPool<ByteBuffer> = KtorDefaultDirectPool
    ) {
        val inputBuf = pool.borrow()
        val outputBuf = pool.borrow()
        val ctx = ZstdDecompressCtx()

        try {
            while (!isClosedForRead) {
                val bytesRead = readAvailable(inputBuf)
                if (bytesRead <= 0) continue

                inputBuf.flip()

                while (inputBuf.hasRemaining()) {
                    outputBuf.clear()
                    ctx.decompressDirectByteBufferStream(outputBuf, inputBuf)

                    outputBuf.flip()
                    destination.writeFully(outputBuf)
                }

                inputBuf.compact()
            }
            inputBuf.flip()
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
        val outputBuf = pool.borrow()
        val ctx = ZstdCompressCtx().apply { setLevel(compressionLevel) }

        try {
            while (!isClosedForRead) {
                inputBuf.clear()
                outputBuf.clear()
                val bytesRead = readAvailable(inputBuf)
                if (bytesRead <= 0) continue
                inputBuf.flip()

                ctx.compress(outputBuf, inputBuf)
                outputBuf.flip()
                destination.writeFully(outputBuf)
            }
        } finally {
            ctx.close()
            pool.recycle(inputBuf)
            pool.recycle(outputBuf)
        }
    }
}
