package org.jetbrains.ktor.cio

import java.io.*
import java.nio.*
import java.util.concurrent.locks.*
import kotlin.concurrent.*
import kotlin.coroutines.experimental.*
import kotlin.coroutines.experimental.intrinsics.*

class OutputStreamChannel : OutputStream(), ReadChannel {
    private val buffer = ByteBuffer.allocate(8192)
    private val lock = ReentrantLock()
    private val notFull = lock.newCondition()
    private var currentContinuation: Continuation<Int>? = null
    private var currentBuffer: ByteBuffer? = null
    private var closed = false

    suspend override fun read(dst: ByteBuffer): Int {
        check(dst.hasRemaining())
        return suspendCoroutineOrReturn { cont ->
            lock.withLock {
                val count = tryRead(dst)
                if (count > 0)
                    count
                else if (closed)
                    -1
                else {
                    check(currentContinuation == null)
                    check(currentBuffer == null)
                    currentContinuation = cont
                    currentBuffer = dst
                    COROUTINE_SUSPENDED
                }
            }
        }
    }

    private fun tryRead(dst: ByteBuffer): Int {
        if (buffer.position() <= 0) {
            return 0
        } else {
            buffer.flip()
            dst.put(buffer)
            val result = buffer.position()
            buffer.compact()
            notFull.signal()
            return result
        }
    }

    override fun write(b: Int) {
        val (cont, result) = lock.withLock {
            while (!buffer.hasRemaining()) {
                notFull.await()
            }
            buffer.put(b.toByte())
            val cont = currentContinuation?.also { currentContinuation = null }
            val result = currentBuffer?.let {
                currentBuffer = null
                tryRead(it)
            }
            cont to result
        }
        cont?.resume(result!!)
    }

    override fun close() {
        super.close()
        lock.withLock {
            closed = true
            currentContinuation?.also { currentContinuation = null }
        }?.resume(-1)
    }
}