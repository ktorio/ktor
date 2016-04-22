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
    fun read(dst: ByteBuffer, handler: AsyncHandler)
}

interface AsyncWriteChannel: Channel {
    fun write(src: ByteBuffer, handler: AsyncHandler)
}

interface SeekableAsyncChannel : AsyncReadChannel {
    val position: Long
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
        // note: it is important to keep it here synchronized even empty to ensure all write operations complete
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

private fun InputStream.read(bb: ByteBuffer): Int {
    val rc = read(bb.array(), bb.arrayOffset() + bb.position(), bb.remaining())

    if (rc > 0) {
        bb.position(bb.position() + rc)
    }

    return rc
}