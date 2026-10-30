/*
 * Copyright 2014-2023 JetBrains s.r.o and contributors. Use of this source code is governed by the Apache 2.0 license.
 */

package io.ktor.util

import io.ktor.utils.io.*
import kotlinx.cinterop.*
import kotlinx.coroutines.CoroutineName
import kotlinx.coroutines.DelicateCoroutinesApi
import kotlinx.coroutines.GlobalScope
import kotlinx.io.EOFException
import kotlinx.io.IOException
import platform.posix.memcpy
import platform.posix.memset
import platform.zlib.*
import kotlin.coroutines.CoroutineContext

/**
 * Implementation of [ContentEncoder] using gzip algorithm
 *
 * [Report a problem](https://ktor.io/feedback/?fqname=io.ktor.util.GZipEncoder)
 */
public actual object GZipEncoder : ContentEncoder, Encoder by ZlibEncoder(gzip = true) {
    actual override val name: String = "gzip"
}

/**
 * Implementation of [ContentEncoder] using deflate algorithm
 *
 * [Report a problem](https://ktor.io/feedback/?fqname=io.ktor.util.DeflateEncoder)
 */
public actual object DeflateEncoder : ContentEncoder, Encoder by ZlibEncoder(gzip = false) {
    actual override val name: String = "deflate"
}

private const val CHUNK_SIZE = 8192

// zlib's window bits: the largest window, and +16 for a gzip wrapper or negated for a raw stream. These are the JVM's
// formats: gzip is a deflate stream in a gzip wrapper, deflate a raw deflate stream with no zlib wrapper.
private const val WINDOW_BITS = 15
private const val MEMORY_LEVEL = 8

private val DeflateWriterCoroutineName = CoroutineName("encoder-deflate-writer")
private val DeflateReaderCoroutineName = CoroutineName("encoder-deflate-reader")
private val InflateWriterCoroutineName = CoroutineName("encoder-inflate-writer")

// The JVM's GZip and Deflate encoders, on the zlib every native target has.
@OptIn(DelicateCoroutinesApi::class)
private class ZlibEncoder(gzip: Boolean) : Encoder {
    private val windowBits = if (gzip) WINDOW_BITS + 16 else -WINDOW_BITS

    override fun encode(source: ByteReadChannel, coroutineContext: CoroutineContext): ByteReadChannel =
        GlobalScope.writer(coroutineContext + DeflateWriterCoroutineName, autoFlush = true) {
            source.deflateTo(channel, windowBits)
        }.channel

    override fun encode(source: ByteWriteChannel, coroutineContext: CoroutineContext): ByteWriteChannel =
        GlobalScope.reader(coroutineContext + DeflateReaderCoroutineName, autoFlush = true) {
            channel.deflateTo(source, windowBits)
        }.channel

    override fun decode(source: ByteReadChannel, coroutineContext: CoroutineContext): ByteReadChannel =
        GlobalScope.writer(coroutineContext + InflateWriterCoroutineName) {
            source.inflateTo(channel, windowBits)
        }.channel
}

private suspend fun ByteReadChannel.deflateTo(destination: ByteWriteChannel, windowBits: Int) {
    val zlib = ZlibStream(deflating = true, windowBits)
    try {
        val bytes = ByteArray(CHUNK_SIZE)
        while (!isClosedForRead) {
            val read = readAvailable(bytes, 0, bytes.size)
            if (read <= 0) continue
            zlib.input(bytes, read)
            zlib.compress(Z_NO_FLUSH, bytes, destination)
        }

        closedCause?.let { throw it }
        zlib.compress(Z_FINISH, bytes, destination)
        destination.flush()
    } finally {
        zlib.end()
    }
}

private suspend fun ByteReadChannel.inflateTo(destination: ByteWriteChannel, windowBits: Int) {
    val zlib = ZlibStream(deflating = false, windowBits)
    try {
        val bytes = ByteArray(CHUNK_SIZE)
        while (!isClosedForRead) {
            val read = readAvailable(bytes, 0, bytes.size)
            if (read <= 0) continue
            zlib.input(bytes, read)
            if (zlib.decompress(bytes, destination)) {
                return
            }
        }

        closedCause?.let { throw it }
        throw EOFException("The compressed stream ended before its end")
    } finally {
        zlib.end()
    }
}

// One zlib stream and its native buffers. The bytes are copied in and out rather than pinned, as the output is
// written between zlib calls, and a write can suspend.
@OptIn(ExperimentalForeignApi::class)
private class ZlibStream(private val deflating: Boolean, windowBits: Int) {
    private val stream = nativeHeap.alloc<z_stream>()
    private val input = nativeHeap.allocArray<UByteVar>(CHUNK_SIZE)
    private val output = nativeHeap.allocArray<UByteVar>(CHUNK_SIZE)

    init {
        // zalloc, zfree and opaque must be null for zlib's own allocator
        memset(stream.ptr, 0, sizeOf<z_stream>().convert())
        val size = sizeOf<z_stream>().toInt()
        val result = if (deflating) {
            deflateInit2_(
                stream.ptr,
                Z_DEFAULT_COMPRESSION,
                Z_DEFLATED,
                windowBits,
                MEMORY_LEVEL,
                Z_DEFAULT_STRATEGY,
                ZLIB_VERSION,
                size
            )
        } else {
            inflateInit2_(stream.ptr, windowBits, ZLIB_VERSION, size)
        }

        if (result != Z_OK) {
            free()
            throw IOException("zlib failed to start: error $result")
        }
    }

    fun input(bytes: ByteArray, length: Int) {
        bytes.usePinned { memcpy(input, it.addressOf(0), length.convert()) }
        stream.next_in = input
        stream.avail_in = length.convert()
    }

    // Deflates the input, writing everything it makes to destination: with Z_FINISH, to the end of the stream.
    suspend fun compress(flush: Int, scratch: ByteArray, destination: ByteWriteChannel) {
        while (true) {
            stream.next_out = output
            stream.avail_out = CHUNK_SIZE.convert()
            val result = deflate(stream.ptr, flush)
            if (result != Z_OK && result != Z_STREAM_END && result != Z_BUF_ERROR) {
                throw IOException("deflate failed: error $result")
            }

            writeOutput(scratch, destination)
            val done = if (flush == Z_FINISH) result == Z_STREAM_END else stream.avail_out != 0u
            if (done) return
        }
    }

    // Inflates the input, writing what it makes to destination; true once the stream has ended, its trailer checked.
    suspend fun decompress(scratch: ByteArray, destination: ByteWriteChannel): Boolean {
        while (true) {
            stream.next_out = output
            stream.avail_out = CHUNK_SIZE.convert()
            val result = inflate(stream.ptr, Z_NO_FLUSH)
            if (result != Z_OK && result != Z_STREAM_END && result != Z_BUF_ERROR) {
                throw IOException("The compressed stream is malformed: zlib error $result")
            }

            writeOutput(scratch, destination)
            if (result == Z_STREAM_END) return true
            if (stream.avail_out != 0u) return false
        }
    }

    private suspend fun writeOutput(scratch: ByteArray, destination: ByteWriteChannel) {
        val produced = CHUNK_SIZE - stream.avail_out.toInt()
        if (produced == 0) return
        scratch.usePinned { memcpy(it.addressOf(0), output, produced.convert()) }
        destination.writeFully(scratch, 0, produced)
    }

    fun end() {
        if (deflating) deflateEnd(stream.ptr) else inflateEnd(stream.ptr)
        free()
    }

    private fun free() {
        nativeHeap.free(stream)
        nativeHeap.free(input)
        nativeHeap.free(output)
    }
}
