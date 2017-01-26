package org.jetbrains.ktor.cio

import java.io.*
import java.nio.*
import java.util.concurrent.locks.*
import kotlin.coroutines.*
import kotlin.coroutines.intrinsics.*

class OutputStreamChannel : OutputStream(), ReadChannel {
    val buffer = ByteBuffer.allocate(8192)
    val lock = ReentrantLock()
    val notFull = lock.newCondition()
    var currentContinuation: Continuation<Int>? = null
    var currentBuffer: ByteBuffer? = null
    var closed = false

    suspend override fun read(dst: ByteBuffer): Int {
        check(dst.hasRemaining())
        return suspendCoroutineOrReturn { cont ->
            locked {
                val count = tryRead(dst)
                if (count > 0)
                    count
                else if (closed) -1
                else {
                    check(currentContinuation == null)
                    check(currentBuffer == null)
                    currentContinuation = cont
                    currentBuffer = dst
                    SUSPENDED_MARKER
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
        val (cont, result) = locked {
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
        locked {
            closed = true
            currentContinuation?.also { currentContinuation = null }
        }?.resume(-1)
    }

    inline fun <T> locked(body: () -> T): T {
        lock.lock()
        try {
            return body()
        } finally {
            lock.unlock()
        }
    }
}