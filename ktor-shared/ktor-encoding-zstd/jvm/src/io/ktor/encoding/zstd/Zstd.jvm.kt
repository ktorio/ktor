/*
 * Copyright 2014-2026 JetBrains s.r.o and contributors. Use of this source code is governed by the Apache 2.0 license.
 */

package io.ktor.encoding.zstd

import com.github.luben.zstd.ZstdCompressCtx
import com.github.luben.zstd.ZstdDecompressCtx
import com.github.luben.zstd.ZstdException
import io.ktor.util.*
import io.ktor.util.cio.*
import io.ktor.utils.io.*
import io.ktor.utils.io.pool.*
import kotlinx.coroutines.DelicateCoroutinesApi
import kotlinx.coroutines.GlobalScope
import kotlinx.io.EOFException
import kotlinx.io.IOException
import java.nio.ByteBuffer
import kotlin.coroutines.CoroutineContext
import com.github.luben.zstd.Zstd as ZstdUtils

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

    internal suspend fun ByteReadChannel.decodeTo(
        destination: ByteWriteChannel,
        pool: ObjectPool<ByteBuffer> = KtorDefaultPool
    ) {
        val inputBuf = pool.borrow()
        val ctx = ZstdDecompressCtx()

        try {
            while (!isClosedForRead) {
                val bytesRead = readAvailable(inputBuf)
                if (bytesRead <= 0) continue

                inputBuf.flip()
                while (inputBuf.hasRemaining()) {
                    val srcOffset = inputBuf.arrayOffset() + inputBuf.position()
                    val srcLength = inputBuf.remaining()

                    val frameCompressedSize = try {
                        ZstdUtils.findFrameCompressedSize(inputBuf.array(), srcOffset, srcLength).toInt()
                    } catch (_: ZstdException) {
                        // an exception could be thrown if the rest of inputBuf does not contain a whole frame,
                        // so we need to break to read the next chunk
                        break
                    }
                    // inputBuf does not contain the whole frame - wait for more data
                    if (frameCompressedSize > srcLength) break

                    val frameContentSize = getFrameContentSize(
                        inputBuf,
                        srcOffset,
                        frameCompressedSize
                    )
                    val outArray = ByteArray(frameContentSize)
                    ctx.decompressByteArray(
                        outArray,
                        0,
                        frameContentSize,
                        inputBuf.array(),
                        srcOffset,
                        frameCompressedSize
                    )
                    destination.writeFully(outArray)
                    inputBuf.position(inputBuf.position() + frameCompressedSize)
                }

                inputBuf.compact()
            }
            inputBuf.flip()
            if (inputBuf.hasRemaining()) {
                throw EOFException("Incomplete zstd frame at end of stream")
            }
        } finally {
            ctx.close()
            pool.recycle(inputBuf)
        }
    }

    private fun getFrameContentSize(
        inputBuf: ByteBuffer,
        srcOffset: Int,
        frameCompressedSize: Int
    ): Int {
        val frameContentSize = ZstdUtils.getFrameContentSize(
            inputBuf.array(),
            srcOffset,
            frameCompressedSize
        )
        if (ZstdUtils.isError(frameContentSize)) {
            throw IOException("Invalid zstd frame: ${ZstdUtils.getErrorName(frameContentSize)}")
        }
        if (frameContentSize == -1L) {
            throw IOException("Content size is unknown")
        }
        return frameContentSize.toInt()
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
