package io.ktor.cio

import java.nio.*
import java.nio.channels.*
import kotlin.coroutines.experimental.*
import kotlin.coroutines.experimental.intrinsics.*

class AsyncFileReadChannel(val source: AsynchronousFileChannel, val start: Long = 0, val endInclusive: Long = source.size() - 1) : RandomAccessReadChannel {
    companion object {
        val completionHandler = object : CompletionHandler<Int, AsyncFileReadChannel> {
            override fun completed(result: Int, channel: AsyncFileReadChannel) {
                channel.position += result
                channel.currentContinuation!!.also { channel.currentContinuation = null }.resume(result)
            }

            override fun failed(exc: Throwable, channel: AsyncFileReadChannel) {
                channel.currentContinuation!!.also { channel.currentContinuation = null }.resumeWithException(exc)
            }
        }
    }

    var currentContinuation: Continuation<Int>? = null

    init {
        require(start >= 0L) { "start position shouldn't be negative but it is $start" }
        require(endInclusive <= source.size() - 1) { "endInclusive points to the position out of the file: file size = ${source.size()}, endInclusive = $endInclusive" }
    }

    override var position: Long = start
    override val size: Long
        get() = source.size()

    suspend override fun read(dst: ByteBuffer): Int {
        val limit = Math.min(dst.remaining().toLong(), endInclusive - position + 1).toInt()
        if (limit <= 0)
            return -1
        dst.limit(dst.position() + limit)
        return suspendCoroutineOrReturn {
            check(currentContinuation == null)
            currentContinuation = it
            source.read(dst, position, this, completionHandler)
            COROUTINE_SUSPENDED
        }
    }

    suspend override fun seek(position: Long) {
        require(position >= 0L) { "position should not be negative: $position" }
        require(position < source.size()) { "position should not run out of the file range: $position !in [0, ${source.size()})" }

        this.position = position
    }

    override fun close() {
        source.close()
    }
}
