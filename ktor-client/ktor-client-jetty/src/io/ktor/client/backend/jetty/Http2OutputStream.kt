package io.ktor.client.backend.jetty

import kotlinx.coroutines.experimental.io.*
import org.eclipse.jetty.http2.api.*
import org.eclipse.jetty.http2.frames.*
import org.eclipse.jetty.util.*
import java.io.*
import java.util.concurrent.*
import java.util.concurrent.locks.*
import kotlin.concurrent.*

class Http2OutputStream(private val stream: Stream) : OutputStream(), Callback {
    private val l = ReentrantLock()
    private val sent = l.newCondition()!!
    private val empty = l.newCondition()!!

    private val failures = ArrayBlockingQueue<Throwable>(1)
    private var outstanding = 0

    fun endBody() {
        stream.data(DataFrame(stream.id, ByteBuffer.allocate(0), true), Callback.NOOP)
    }

    override fun succeeded() {
        resume(false)
    }

    override fun failed(x: Throwable) {
        failures.offer(x)
        resume(true)
    }

    private fun resume(all: Boolean) {
        l.withLock {
            outstanding--
            sent.signalAll()

            if (all || outstanding == 0) {
                empty.signalAll()
            }
        }
    }

    override fun write(b: Int) {
        writeEnter()

        val f = DataFrame(stream.id, ByteBuffer.wrap(byteArrayOf(b.toByte()), 0, 1), false)
        stream.data(f, this)
    }

    override fun write(b: ByteArray, off: Int, len: Int) {
        writeEnter()

        val f = DataFrame(stream.id, ByteBuffer.wrap(b, off, len), false)
        stream.data(f, this)
    }

    private fun writeEnter() {
        l.withLock {
            checkFailures()

            while (outstanding == 5) {
                sent.await()
                checkFailures()
            }

            outstanding++
        }
    }

    override fun flush() {
        l.withLock {
            while (true) {
                checkFailures()
                if (outstanding == 0) break
                empty.await()
            }
        }
    }

    private fun checkFailures() {
        failures.peek()?.let { throw it }
    }

}