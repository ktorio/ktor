package org.jetbrains.ktor.cio

import java.io.*
import java.nio.*
import java.nio.channels.*
import java.nio.file.*
import kotlin.coroutines.*
import kotlin.coroutines.intrinsics.*

class FileReadChannel(val fileChannel: AsynchronousFileChannel, val start: Long = 0, val endInclusive: Long = fileChannel.size() - 1) : RandomAccessReadChannel {
    companion object {
        val completionHandler = object : CompletionHandler<Int, Continuation<Int>> {
            override fun completed(result: Int, continuation: Continuation<Int>) = continuation.resume(result)
            override fun failed(exc: Throwable, attachment: Continuation<Int>) = attachment.resumeWithException(exc)
        }
    }

    init {
        require(start >= 0L) { "start position shouldn't be negative but it is $start" }
        require(endInclusive <= fileChannel.size() - 1) { "endInclusive points to the position out of the file: file size = ${fileChannel.size()}, endInclusive = $endInclusive" }
    }

    override val size: Long
        get() = fileChannel.size()

    suspend override fun read(dst: ByteBuffer): Int {
        dst.clear()
        val limit = Math.min(dst.capacity().toLong(), endInclusive - position + 1).toInt()
        if (limit <= 0)
            return -1
        dst.limit(limit)
        val result = suspendCoroutine<Int> {
            // TODO: use unsafe variant of suspendCoroutine to avoid SafeContinuation allocation
            fileChannel.read(dst, position, it, completionHandler)
            SUSPENDED_MARKER
        }
        position += result
        return result
    }

    override var position: Long = start

    suspend override fun seek(position: Long) {
        require(position >= 0L) { "position should not be negative: $position" }
        require(position < fileChannel.size()) { "position should not run out of the file range: $position !in [0, ${fileChannel.size()})" }

        this.position = position
    }

    override fun close() {
        fileChannel.close()
    }
}

fun Path.asyncReadOnlyFileChannel(start: Long = 0, endInclusive: Long = Files.size(this) - 1): FileReadChannel {
    val asyncFileChannel = AsynchronousFileChannel.open(this, StandardOpenOption.READ)
    return FileReadChannel(asyncFileChannel, start, endInclusive)
}

fun File.asyncReadOnlyFileChannel(start: Long = 0, endInclusive: Long = length() - 1): FileReadChannel {
    return toPath().asyncReadOnlyFileChannel(start, endInclusive)
}
