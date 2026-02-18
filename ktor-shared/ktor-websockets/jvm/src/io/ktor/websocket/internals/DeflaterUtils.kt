/*
 * Copyright 2014-2021 JetBrains s.r.o and contributors. Use of this source code is governed by the Apache 2.0 license.
 */

package io.ktor.websocket.internals

import io.ktor.util.cio.*
import io.ktor.utils.io.core.*
import io.ktor.utils.io.pool.*
import io.ktor.websocket.MAX_INFLATED_FRAME_SIZE
import kotlinx.io.*
import java.nio.*
import java.util.zip.*

private val PADDED_EMPTY_CHUNK: ByteArray = byteArrayOf(0, 0, 0, 0xff.toByte(), 0xff.toByte())
private val EMPTY_CHUNK: ByteArray = byteArrayOf(0, 0, 0xff.toByte(), 0xff.toByte())

internal fun Deflater.deflateFully(data: ByteArray): ByteArray {
    setInput(data)

    val deflatedBytes = buildPacket {
        KtorDefaultPool.useInstance { buffer ->
            while (!needsInput()) {
                deflateTo(this@deflateFully, buffer, false)
            }

            while (deflateTo(this@deflateFully, buffer, true) != 0) {}
        }
    }

    if (deflatedBytes.endsWith(PADDED_EMPTY_CHUNK)) {
        return deflatedBytes.readByteArray(deflatedBytes.remaining.toInt() - EMPTY_CHUNK.size).also {
            deflatedBytes.close()
        }
    }

    return buildPacket {
        writePacket(deflatedBytes)
        writeByte(0)
    }.readByteArray()
}

internal fun Inflater.inflateFully(data: ByteArray): ByteArray =
    inflateFully(data, MAX_INFLATED_FRAME_SIZE)

internal fun Inflater.inflateFully(data: ByteArray, maxOutputSize: Int): ByteArray {
    require(maxOutputSize >= 0) { "maxOutputSize should be >= 0" }
    reset()

    val dataToInflate = data + EMPTY_CHUNK
    setInput(dataToInflate)

    var totalWritten: Long = 0

    val packet = buildPacket {
        KtorDefaultPool.useInstance { buffer ->
            while (true) {
                if (finished()) break

                buffer.clear()
                val inflated = inflate(buffer.array(), buffer.position(), buffer.limit())

                if (inflated > 0) {
                    buffer.position(buffer.position() + inflated)
                    buffer.flip()

                    totalWritten += buffer.remaining().toLong()
                    if (totalWritten > maxOutputSize.toLong()) {
                        throw IOException("Inflated data exceeds limit: $totalWritten > $maxOutputSize")
                    }

                    writeFully(buffer)
                    continue
                }

                if (needsDictionary()) {
                    throw IOException("Inflater needs a preset dictionary")
                }

                if (needsInput()) {
                    break
                }

                throw IOException("Inflater made no progress; data probably corrupted")
            }
        }
    }

    return packet.readByteArray()
}

private fun Sink.deflateTo(
    deflater: Deflater,
    buffer: ByteBuffer,
    flush: Boolean
): Int {
    buffer.clear()

    val deflated = if (flush) {
        deflater.deflate(buffer.array(), buffer.position(), buffer.limit(), Deflater.SYNC_FLUSH)
    } else {
        deflater.deflate(buffer.array(), buffer.position(), buffer.limit())
    }

    if (deflated == 0) {
        return 0
    }

    buffer.position(buffer.position() + deflated)
    buffer.flip()
    writeFully(buffer)

    return deflated
}
