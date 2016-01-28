package org.jetbrains.ktor.servlet

import org.jetbrains.ktor.application.*
import org.jetbrains.ktor.host.*
import org.jetbrains.ktor.http.*
import org.jetbrains.ktor.interception.*
import org.jetbrains.ktor.nio.*
import java.io.*
import java.nio.channels.*
import java.nio.file.*
import javax.servlet.*
import javax.servlet.http.*

class ServletApplicationResponse(override val call: ServletApplicationCall, val servletRequest: HttpServletRequest, val servletResponse: HttpServletResponse) : BaseApplicationResponse(call) {
    var _status: HttpStatusCode? = null
    override val status = Interceptable1<HttpStatusCode, Unit> { code ->
        _status = code
        servletResponse.status = code.value
    }

    override val headers: ResponseHeaders = object : ResponseHeaders() {
        override fun hostAppendHeader(name: String, value: String) {
            servletResponse.addHeader(name, value)
        }

        override fun getHostHeaderNames(): List<String> = servletResponse.headerNames.toList()
        override fun getHostHeaderValues(name: String): List<String> = servletResponse.getHeaders(name).toList()
    }

    override fun status(): HttpStatusCode? = _status

    override val stream = Interceptable1<OutputStream.() -> Unit, Unit> { body ->
        servletResponse.outputStream.body()
        ApplicationCallResult.Handled
    }

    override fun sendStream(stream: InputStream) {
        stream {
            if (this is ServletOutputStream) {
                val asyncContext = startAsync()

                val pump = AsyncInputStreamPump(stream, asyncContext, this)
                pump.start()
            } else {
                stream.use { it.copyTo(this) }
            }
        }
    }

    override fun sendFile(file: File, position: Long, length: Long) {
        stream {
            if (this is ServletOutputStream) {
                val asyncContext = startAsync()

                AsyncChannelPump(
                        file.asyncReadOnlyFileChannel(position, position + length - 1),
                        asyncContext,
                        servletResponse.outputStream,
                        call.application.config.log).start()
            } else {
                file.inputStream().use { it.copyTo(this) }
            }
        }
    }

    override fun sendAsyncChannel(channel: AsynchronousByteChannel) {
        stream {
            if (this is ServletOutputStream) {
                val asyncContext = startAsync()
                AsyncChannelPump(channel, asyncContext, this, call.application.config.log).start()
            } else {
                Channels.newInputStream(channel).copyTo(this)
            }
        }
    }

    private fun startAsync(): AsyncContext {
        val asyncContext = servletRequest.startAsync(servletRequest, servletResponse)
        // asyncContext.timeout = ?
        call.continueAsync(asyncContext)

        return asyncContext
    }
}