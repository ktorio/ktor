package org.jetbrains.ktor.cio

import java.io.*
import java.nio.*
import java.nio.channels.*
import java.nio.file.*
import kotlin.coroutines.*
import kotlin.coroutines.intrinsics.*

class FileReadChannel(val source: AsynchronousFileChannel, val start: Long = 0, val endInclusive: Long = source.size() - 1) : RandomAccessReadChannel {
    companion object {
        val completionHandler = object : CompletionHandler<Int, FileReadChannel> {
            override fun completed(result: Int, channel: FileReadChannel) {
                channel.position += result
                channel.currentContinuation!!.also { channel.currentContinuation = null }.resume(result)
            }
            override fun failed(exc: Throwable, channel: FileReadChannel) {
                channel.currentContinuation!!.also { channel.currentContinuation = null }.resumeWithException(exc)
            }
        }
    }

    var currentContinuation : Continuation<Int>? = null

    init {
        require(start >= 0L) { "start position shouldn't be negative but it is $start" }
        require(endInclusive <= source.size() - 1) { "endInclusive points to the position out of the file: file size = ${source.size()}, endInclusive = $endInclusive" }
    }

    override val size: Long
        get() = source.size()

    suspend override fun read(dst: ByteBuffer): Int {
        val limit = Math.min(dst.remaining().toLong(), endInclusive - position + 1).toInt()
        if (limit <= 0)
            return -1
        dst.limit(dst.position() + limit)
        return suspendCoroutineOrReturn<Int> {
            check(currentContinuation == null)
            currentContinuation = it
            source.read(dst, position, this, completionHandler)
            SUSPENDED_MARKER
        }
    }

    override var position: Long = start

    suspend override fun seek(position: Long) {
        require(position >= 0L) { "position should not be negative: $position" }
        require(position < source.size()) { "position should not run out of the file range: $position !in [0, ${source.size()})" }

        this.position = position
    }

    override fun close() {
        source.close()
    }
}

fun Path.asyncReadOnlyFileChannel(start: Long = 0, endInclusive: Long = Files.size(this) - 1): FileReadChannel {
    val asyncFileChannel = AsynchronousFileChannel.open(this, StandardOpenOption.READ)
    return FileReadChannel(asyncFileChannel, start, endInclusive)
}

fun File.asyncReadOnlyFileChannel(start: Long = 0, endInclusive: Long = length() - 1): FileReadChannel {
    return toPath().asyncReadOnlyFileChannel(start, endInclusive)
}
