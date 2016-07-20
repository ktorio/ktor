package org.jetbrains.ktor.nio

import org.jetbrains.ktor.util.*
import java.io.*
import java.nio.*
import java.util.concurrent.*

interface AsyncHandler {
    fun success(count: Int)
    fun successEnd()
    fun failed(cause: Throwable)
}

interface Channel : Closeable {
}

interface ReadChannel : Channel {
    /**
     * Initiates read operation. If there is already read operation pending then it fails with exception.
     * Fires [AsyncHandler.success] with read count, [AsyncHandler.successEnd] if EOF or [AsyncHandler.failed].
     * Notice that the [handler] could be notified before the [read] function returns.
     */
    fun read(dst: ByteBuffer, handler: AsyncHandler)

    /**
     * Checks for pending flush requests. Resets counter and returns value. Can be called at any moment.
     */
    fun releaseFlush(): Int = 0
}

interface WriteChannel : Channel {
    /**
     * Initiates write operation. If there is already write operation pending then it fails with exception.
     * Fires [AsyncHandler.success] with write count or [AsyncHandler.failed].
     * Notice that the [handler] could be notified before the [write] function returns.
     */
    fun write(src: ByteBuffer, handler: AsyncHandler)

    /**
     * Request write flush. In fact there are no any guarantees that the data will be received by a remote peer.
     * Use it when you want to ensure that there is no pending data remains. Notice that the function is asynchronous,
     * it returns immediately and there are no guarantees when the flush will be actually performed.
     */
    fun requestFlush() {
    }

    /**
     * Request write flush. In fact there are no any guarantees that the data will be received by a remote peer.
     * Use it when you want to ensure that there is no pending data remains. Notice that the function is asynchronous,
     * it returns immediately and there are no guarantees when the flush will be actually performed.
     * But for sure the [handler] will be notified ([AsyncHandler.successEnd] or [AsyncHandler.failed] will be called).
     * Handler could be notified in the future or immediately.
     */
    fun flush(handler: AsyncHandler) {
        requestFlush()
        handler.successEnd()
    }
}

interface SeekableChannel : ReadChannel {
    /**
     * Current channel read position. If there is seek operation in progress then the behaviour is not defined.
     */
    val position: Long

    /**
     * Initiates seek operation that fires [AsyncHandler.successEnd] or [AsyncHandler.failed].
     * Could be sync or async so handler could block [seek] function.
     */
    fun seek(position: Long, handler: AsyncHandler)
}

interface ProgressListener<T> {
    fun progress(source: T)
}

fun <T> CompletableFuture<T>.asHandler(block: (Int?) -> T) = object : AsyncHandler {
    override fun success(count: Int) {
        this@asHandler.complete(block(count))
    }

    override fun successEnd() {
        this@asHandler.complete(block(null))
    }

    override fun failed(cause: Throwable) {
        this@asHandler.completeExceptionally(cause)
    }
}

private class AsyncReadChannelAdapterStream(val ch: ReadChannel) : InputStream() {
    private val singleByte = ByteBuffer.allocate(1)
    private val adapter = BlockingAdapter()

    tailrec
    override fun read(): Int {
        singleByte.clear()
        ch.read(singleByte, adapter.handler)
        val rc = adapter.await()
        when (rc) {
            -1 -> return -1
            0 -> return read()
        }

        singleByte.flip()
        return singleByte.get().toInt() and 0xff
    }

    tailrec
    override fun read(b: ByteArray, off: Int, len: Int): Int {
        if (len == 0) return 0

        val bb = ByteBuffer.wrap(b, off, len)
        ch.read(bb, adapter.handler)
        val rc = adapter.await()

        return when (rc) {
            0 -> read(b, off, len)
            else -> rc
        }
    }

    override fun close() {
        ch.close()
    }
}

fun ReadChannel.asInputStream(): InputStream = AsyncReadChannelAdapterStream(this)

private class AsyncWriteChannelAdapterStream(val ch: WriteChannel) : OutputStream() {
    private val singleByte = ByteBuffer.allocate(1)
    private val adapter = BlockingAdapter()

    @Synchronized
    override fun write(b: Int) {
        do {
            singleByte.clear()
            singleByte.put(b.toByte())
            ch.write(singleByte, adapter.handler)
        } while (adapter.await() <= 0)
    }

    @Synchronized
    override fun write(b: ByteArray, off: Int, len: Int) {
        val bb = ByteBuffer.wrap(b, off, len)

        while (bb.hasRemaining()) {
            ch.write(bb, adapter.handler)
            adapter.await()
        }
    }

    @Synchronized
    override fun close() {
        ch.requestFlush()
        singleByte.limit(0)
        ch.write(singleByte, adapter.handler)
        ch.requestFlush()
        adapter.await()

        ch.close()
    }

    @Synchronized
    override fun flush() {
        ch.requestFlush()
    }
}

fun WriteChannel.asOutputStream(): OutputStream = AsyncWriteChannelAdapterStream(this)

private class InputStreamReadChannelAdapter(val input: InputStream) : ReadChannel {
    override fun read(dst: ByteBuffer, handler: AsyncHandler) {
        try {
            val rc = input.read(dst)
            when {
                rc == -1 -> handler.successEnd()
                else -> handler.success(rc)
            }
        } catch (t: Throwable) {
            handler.failed(t)
        }
    }

    override fun close() {
        input.close()
    }
}

fun InputStream.asAsyncChannel(): ReadChannel = InputStreamReadChannelAdapter(this)

private class OutputStreamWriteChannelAdapter(val output: OutputStream) : WriteChannel {
    override fun write(src: ByteBuffer, handler: AsyncHandler) {
        val size = src.remaining()

        val buffer = if (src.hasArray()) {
            src
        } else {
            src.copy()
        }

        output.write(buffer.array(), buffer.arrayOffset() + buffer.position(), size)

        src.position(src.limit())
        handler.success(size)
    }

    override fun close() {
        output.close()
    }

    override fun requestFlush() {
        output.flush()
    }
}

fun OutputStream.asWriteChannel(): WriteChannel = OutputStreamWriteChannelAdapter(this)

fun WriteChannel.writeFully(bb: ByteBuffer, handler: AsyncHandler) {
    val initialSize = bb.remaining()
    val innerHandler = object : AsyncHandler {
        override fun success(count: Int) {
            if (bb.hasRemaining()) {
                write(bb, this)
            } else {
                handler.success(initialSize)
            }
        }

        override fun successEnd() {
        }

        override fun failed(cause: Throwable) {
            handler.failed(cause)
        }
    }

    write(bb, innerHandler)
}

fun asyncHandler(handler: (Throwable?) -> Unit) = object : AsyncHandler {
    override fun success(count: Int) {
        handler(null)
    }

    override fun successEnd() {
        handler(null)
    }

    override fun failed(cause: Throwable) {
        handler(cause)
    }
}

private fun InputStream.read(bb: ByteBuffer): Int {
    val rc = read(bb.array(), bb.arrayOffset() + bb.position(), bb.remaining())

    if (rc > 0) {
        bb.position(bb.position() + rc)
    }

    return rc
}