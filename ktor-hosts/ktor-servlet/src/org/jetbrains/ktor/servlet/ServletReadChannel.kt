package org.jetbrains.ktor.servlet

import kotlinx.coroutines.experimental.channels.*
import org.jetbrains.ktor.cio.*
import java.io.*
import java.nio.*
import java.nio.channels.*
import java.util.concurrent.atomic.*
import javax.servlet.*
import kotlin.coroutines.experimental.*
import kotlin.coroutines.experimental.intrinsics.*

class ServletReadChannel(private val servletInputStream: ServletInputStream) : ReadChannel {
    @Volatile
    private var listenerInstalled = 0

    @Volatile
    private var readInProgress = 0

    private val callbackState = ConflatedChannel<Unit>()

    private val readListener = object : ReadListener {
        override fun onAllDataRead() {
            callbackState.close()
        }

        override fun onError(t: Throwable) {
            callbackState.close(if (t is IOException && t !is EOFException) ChannelReadException("Servlet ReadListener.onError($t)", t) else t)
        }

        override fun onDataAvailable() {
            callbackState.offer(Unit)
        }
    }

    suspend override fun read(dst: ByteBuffer): Int {
        startReading()

        if (tryPollState()) return -1

        if (listenerInstalled == 0 && ListenerInstalled.compareAndSet(this, 0, 1)) {
            return installAndRead(dst)
        }

        return if (servletInputStream.isFinished) finish()
        else if (servletInputStream.isReady) {
            doRead(dst)
        } else {
            readSuspendUnconfined(dst)
        }
    }

    private suspend fun installAndRead(dst: ByteBuffer): Int {
        servletInputStream.setReadListener(readListener)
        return readSuspendUnconfined(dst)
    }

    private suspend fun readSuspendUnconfined(dst: ByteBuffer): Int {
        return suspendCoroutineOrReturn { cont ->
            val completion = object : Continuation<Int> by cont {
                override val context: CoroutineContext get() = EmptyCoroutineContext
            }

            bufferForLambda = dst
            readSuspendFunction.startCoroutineUninterceptedOrReturn(completion)
        }

        // ~= return run(Unconfined) { readSuspend(dst) }
    }

    private fun <T> suspend(block: suspend () -> T): suspend () -> T = block

    @Volatile
    private var bufferForLambda: ByteBuffer = Empty

    private val readSuspendFunction = suspend {
        readSuspend(bufferForLambda).also { bufferForLambda = Empty }
    }

    private tailrec suspend fun readSuspend(dst: ByteBuffer): Int {
        if (receiveState()) return -1

        return when {
            servletInputStream.isFinished -> finish()
            servletInputStream.isReady -> doRead(dst)
            else -> readSuspend(dst)
        }
    }

    private suspend fun receiveState(): Boolean {
        try {
            if (callbackState.receiveOrNull() == null) {
                endReading()
                return true
            }
        } catch (eof: EOFException) {
            return false
        } catch (t: Throwable) {
            endReading()
            throw t
        }

        return false
    }

    private fun tryPollState(): Boolean {
        try {
            if (callbackState.poll() == null && callbackState.isClosedForReceive) {
                endReading()
                return true
            }
        } catch (eof: EOFException) {
            return false
        } catch (t: Throwable) {
            endReading()
            throw t
        }

        return false
    }

    private fun startReading() {
        if (!ReadInProgress.compareAndSet(this, 0, 1)) throw IllegalStateException("read() is already in progress")
    }

    private fun endReading() {
        readInProgress = 0
    }

    private fun finish(): Int {
        callbackState.close()
        endReading()

        return -1
    }

    override fun close() {
        try {
            callbackState.close(ChannelReadException("Channel has been closed via close() call", ClosedChannelException()))
        } finally {
            servletInputStream.close()
        }
    }

    private fun doRead(buffer: ByteBuffer): Int {
        try {
            return servletInputStream.read(buffer)
        } catch (e: EOFException) {
            return finish()
        } catch (exception: IOException) {
            throw ChannelReadException(exception = exception)
        } finally {
            endReading()
        }
    }

    private fun ServletInputStream.read(buffer: ByteBuffer): Int {
        if (buffer.hasArray()) {
            val size = read(buffer.array(), buffer.arrayOffset() + buffer.position(), buffer.remaining())
            if (size > 0) {
                buffer.position(buffer.position() + size)
            }
            return size
        } else {
            val tempArray = ByteArray(buffer.remaining())
            val size = read(tempArray)
            if (size > 0) {
                buffer.put(tempArray, 0, size)
            }
            return size
        }
    }

    companion object {
        private val Empty: ByteBuffer = ByteBuffer.allocate(0)

        private val ReadInProgress = AtomicIntegerFieldUpdater.newUpdater(ServletReadChannel::class.java, ServletReadChannel::readInProgress.name)!!

        private val ListenerInstalled = AtomicIntegerFieldUpdater.newUpdater(ServletReadChannel::class.java, ServletReadChannel::listenerInstalled.name)
    }
}