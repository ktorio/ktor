package org.jetbrains.ktor.servlet

import org.jetbrains.ktor.application.*
import org.jetbrains.ktor.host.*
import org.jetbrains.ktor.interception.*
import org.jetbrains.ktor.nio.*
import org.jetbrains.ktor.util.*
import java.io.*
import java.nio.channels.*
import javax.servlet.*
import javax.servlet.http.*

class ServletApplicationCall(application: Application,
                                    private val servletRequest: HttpServletRequest,
                                    private val servletResponse: HttpServletResponse) : BaseApplicationCall(application) {

    override val attributes = Attributes()
    override val request : ApplicationRequest = ServletApplicationRequest(servletRequest)
    override val response : ApplicationResponse = ServletApplicationResponse(servletResponse)
    override val parameters: ValuesMap get() = request.parameters

    private var asyncContext: AsyncContext? = null

    fun continueAsync(asyncContext: AsyncContext) {
        // TODO: assert that continueAsync was not yet called
        this.asyncContext = asyncContext
    }

    val asyncStarted: Boolean
        get() = asyncContext != null

    var completed: Boolean = false
    override val close = Interceptable0 {
        completed = true
        servletResponse.flushBuffer()
        if (asyncContext != null) {
            asyncContext?.complete()
        }
    }

    override fun sendStream(stream: InputStream) {
        response.stream {
            if (this is ServletOutputStream) {
                val asyncContext = startAsync()

                val pump = AsyncInputStreamPump(stream, asyncContext, this)
                pump.start()
            } else {
                stream.use { it.copyTo(this) }
            }
        }
    }

    private fun startAsync(): AsyncContext {
        val asyncContext = servletRequest.startAsync(servletRequest, servletResponse)
        // asyncContext.timeout = ?
        continueAsync(asyncContext)

        return asyncContext
    }


    override fun sendFile(file: File, position: Long, length: Long) {
        response.stream {
            if (this is ServletOutputStream) {
                val asyncContext = startAsync()
                val fileChannel = file.asyncReadOnlyFileChannel(position, position + length - 1)
                AsyncChannelPump(
                        fileChannel,
                        asyncContext,
                        servletResponse.outputStream,
                        application.config.log).start()
            } else {
                file.inputStream().use { it.copyTo(this) }
            }
        }
    }

    override fun sendAsyncChannel(channel: AsynchronousByteChannel) {
        response.stream {
            if (this is ServletOutputStream) {
                val asyncContext = startAsync()
                AsyncChannelPump(channel, asyncContext, this, application.config.log).start()
            } else {
                Channels.newInputStream(channel).copyTo(this)
            }
        }
    }

}