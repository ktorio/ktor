/*
 * Copyright 2014-2026 JetBrains s.r.o and contributors. Use of this source code is governed by the Apache 2.0 license.
 */

package io.ktor.encoding.zstd

import com.github.luben.zstd.ZstdCompressCtx
import com.github.luben.zstd.ZstdDecompressCtx
import io.ktor.util.ContentEncoder
import io.ktor.util.Encoder
import io.ktor.util.cio.KtorDefaultPool
import io.ktor.utils.io.ByteReadChannel
import io.ktor.utils.io.ByteWriteChannel
import io.ktor.utils.io.pool.ObjectPool
import io.ktor.utils.io.readAvailable
import io.ktor.utils.io.reader
import io.ktor.utils.io.writeFully
import io.ktor.utils.io.writer
import kotlinx.coroutines.DelicateCoroutinesApi
import kotlinx.coroutines.GlobalScope
import java.nio.ByteBuffer
import kotlin.coroutines.CoroutineContext
import com.github.luben.zstd.Zstd as ZstdUtils

/**
 * Implementation of [ContentEncoder] using zstd algorithm
 *
 * [Report a problem](https://ktor.io/feedback/?fqname=io.ktor.util.ZstdEncoder)
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
 * [Report a problem](https://ktor.io/feedback/?fqname=io.ktor.util.Zstd)
 */
public class Zstd(private val compressionLevel: Int) : Encoder {

    @OptIn(DelicateCoroutinesApi::class)
    override fun encode(source: ByteReadChannel, coroutineContext: CoroutineContext): ByteReadChannel =
        GlobalScope.writer(coroutineContext, autoFlush = true) {
            source.encodeTo(channel, KtorDefaultPool, compressionLevel)
        }.channel

    @OptIn(DelicateCoroutinesApi::class)
    override fun encode(source: ByteWriteChannel, coroutineContext: CoroutineContext): ByteWriteChannel =
        GlobalScope.reader(coroutineContext, autoFlush = true) {
            channel.encodeTo(source, KtorDefaultPool, compressionLevel)
        }.channel

    @OptIn(DelicateCoroutinesApi::class)
    override fun decode(
        source: ByteReadChannel,
        coroutineContext: CoroutineContext
    ): ByteReadChannel = GlobalScope.writer(coroutineContext) {
        source.decodeTo(channel, KtorDefaultPool)
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
                val bytesRead = readAvailable(inputBuf)
                if (bytesRead <= 0 && inputBuf.position() == 0) {
                    if (isClosedForRead) break
                    continue
                }

                inputBuf.flip()

                while (inputBuf.hasRemaining()) {
                    val frameSize = ZstdUtils.getFrameContentSize(
                        inputBuf.array(),
                        inputBuf.arrayOffset() + inputBuf.position(),
                        inputBuf.remaining()
                    )

                    if (frameSize > outputBuf.capacity()) {
                        val tempOutput = ByteArray(frameSize.toInt())
                        val compressedData = ByteArray(inputBuf.remaining())
                        inputBuf.get(compressedData)

                        val decompressedSize = ZstdUtils.decompress(tempOutput, compressedData).toInt()
                        destination.writeFully(tempOutput, 0, decompressedSize)
                        break
                    }

                    outputBuf.clear()
                    val decompressedSize = ctx.decompressByteArray(
                        outputBuf.array(),
                        outputBuf.arrayOffset(),
                        outputBuf.capacity(),
                        inputBuf.array(),
                        inputBuf.arrayOffset() + inputBuf.position(),
                        inputBuf.remaining()
                    )

                    if (decompressedSize > 0) {
                        destination.writeFully(outputBuf.array(), 0, decompressedSize)
                        // Update position: decompressByteArray for streaming context doesn't return consumed size easily.
                        // In this loop, we consume the remaining input chunk.
                        inputBuf.position(inputBuf.limit())
                    } else {
                        break
                    }
                }
                inputBuf.compact()
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
        val outputBuf = pool.borrow()
        val ctx = ZstdCompressCtx().apply { setLevel(compressionLevel) }

        try {
            while (!isClosedForRead) {
                inputBuf.clear()
                val bytesRead = readAvailable(inputBuf)
                if (bytesRead <= 0) continue

                val maxCompressedSize = ZstdUtils.compressBound(bytesRead.toLong()).toInt()
                val outArray = if (maxCompressedSize > outputBuf.capacity()) {
                    ByteArray(maxCompressedSize)
                } else {
                    outputBuf.array()
                }
                val compressedSize = ctx.compressByteArray(
                    outArray,
                    0,
                    outArray.size,
                    inputBuf.array(),
                    0,
                    bytesRead
                )

                destination.writeFully(outArray, 0, compressedSize)
            }
        } finally {
            ctx.close()
            pool.recycle(inputBuf)
            pool.recycle(outputBuf)
        }
    }
}
