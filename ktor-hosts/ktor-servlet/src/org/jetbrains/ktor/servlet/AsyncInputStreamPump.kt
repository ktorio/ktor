package org.jetbrains.ktor.servlet

import java.io.*
import javax.servlet.*

/**
 * Transfers content from the specified [stream] to [servletOutputStream].
 *
 * Notice that you should startAsync before use this
 *
 * This pump does async only for *write* operation but reading from the [stream] is blocking
 */
internal class AsyncInputStreamPump(val stream: InputStream, val asyncContext: AsyncContext, val servletOutputStream: ServletOutputStream) {

    private val buffer = ByteArray(4096)

    fun start() {
        servletOutputStream.setWriteListener(object : WriteListener {
            override fun onWritePossible() {
                readWriteLoop()
            }

            override fun onError(t: Throwable?) {
                asyncContext.complete()
            }
        })
    }

    private fun complete() {
        try {
            stream.close()
            servletOutputStream.flush()
        } finally {
            asyncContext.complete()
        }
    }

    private fun readWriteLoop() {
        while (servletOutputStream.isReady) {
            val read = stream.read(buffer) // blocking read here
            if (read == -1) {
                complete()
                break
            } else {
                servletOutputStream.write(buffer, 0, read)
            }
        }
    }
}
