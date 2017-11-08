package io.ktor.client.backend.jetty

import kotlinx.coroutines.experimental.io.*
import org.eclipse.jetty.http2.api.*
import org.eclipse.jetty.http2.frames.*
import org.eclipse.jetty.util.*
import java.util.concurrent.*
import java.util.concurrent.locks.*
import kotlin.concurrent.*

internal class Http2Request(private val stream: Stream) : Callback {
    private val l = ReentrantLock()
    private val sent = l.newCondition()!!
    private val empty = l.newCondition()!!

    private val failures = ArrayBlockingQueue<Throwable>(1)
    private var outstanding = 0

    override fun succeeded() {
        resume(false)
    }

    override fun failed(x: Throwable) {
        failures.offer(x)
        resume(true)
    }

    fun write(src: ByteBuffer) {
        writeEnter()
        val frame = DataFrame(stream.id, src, false)
        stream.data(frame, this)
    }

    fun flush() {
        l.withLock {
            while (true) {
                checkFailures()
                if (outstanding == 0) break
                empty.await()
            }
        }
    }

    fun endBody() {
        stream.data(DataFrame(stream.id, ByteBuffer.allocate(0), true), Callback.NOOP)
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

    private fun resume(all: Boolean) {
        l.withLock {
            outstanding--
            sent.signalAll()

            if (all || outstanding == 0) {
                empty.signalAll()
            }
        }
    }

    private fun checkFailures() {
        failures.peek()?.let { throw it }
    }
}