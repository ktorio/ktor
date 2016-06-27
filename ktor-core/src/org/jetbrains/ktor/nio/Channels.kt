package org.jetbrains.ktor.nio

import java.io.*
import java.nio.*
import java.util.concurrent.*

interface AsyncHandler {
    fun success(count: Int)
    fun successEnd()
    fun failed(cause: Throwable)
}

interface Channel: Closeable {
}
interface AsyncReadChannel: Channel {
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

interface AsyncWriteChannel: Channel {
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
}

interface SeekableAsyncChannel : AsyncReadChannel {
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

/**
 * Very similar to CompletableFuture.asHandler but can be used multiple times
 */
class BlockingAdapter {
    private val semaphore = Semaphore(0)
    private var error: Throwable? = null
    private var count: Int = -1

    val handler = object : AsyncHandler {
        override fun success(count: Int) {
            error = null
            this@BlockingAdapter.count = count
            semaphore.release()
        }

        override fun successEnd() {
            count = -1
            error = null
            semaphore.release()
        }

        override fun failed(cause: Throwable) {
            error = cause
            semaphore.release()
        }
    }

    fun await(): Int {
        count
        semaphore.acquire()
        error?.let { throw it }
        return count
    }
}

private class AsyncReadChannelAdapterStream(val ch: AsyncReadChannel) : InputStream() {
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

fun AsyncReadChannel.asInputStream(): InputStream = AsyncReadChannelAdapterStream(this)

private class AsyncWriteChannelAdapterStream(val ch: AsyncWriteChannel) : OutputStream() {
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
        ch.close()
    }

    @Synchronized
    override fun flush() {
        ch.requestFlush()
    }
}

fun AsyncWriteChannel.asOutputStream(): OutputStream = AsyncWriteChannelAdapterStream(this)

private class InputStreamReadChannelAdapter(val input: InputStream) : AsyncReadChannel {
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

fun InputStream.asAsyncChannel(): AsyncReadChannel = InputStreamReadChannelAdapter(this)

fun AsyncWriteChannel.writeFully(bb: ByteBuffer, handler: AsyncHandler) {
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

fun onCompletedHandler(handler: (Throwable?) -> Unit) = object : AsyncHandler {
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