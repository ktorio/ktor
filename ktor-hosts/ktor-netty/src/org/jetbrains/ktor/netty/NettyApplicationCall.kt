package org.jetbrains.ktor.netty

import io.netty.channel.*
import io.netty.handler.codec.http.*
import org.jetbrains.ktor.application.*
import org.jetbrains.ktor.host.*
import org.jetbrains.ktor.interception.*
import org.jetbrains.ktor.util.*
import java.io.*
import java.nio.channels.*

class NettyApplicationCall(application: Application,
                                  val context: ChannelHandlerContext,
                                  val httpRequest: FullHttpRequest) : BaseApplicationCall(application) {

    var completed: Boolean = false

    val httpResponse = DefaultHttpResponse(HttpVersion.HTTP_1_1, HttpResponseStatus.OK)

    override val request: ApplicationRequest = NettyApplicationRequest(httpRequest)
    override val response: ApplicationResponse = NettyApplicationResponse(httpRequest, httpResponse, context)
    override val attributes = Attributes()

    init {
        HttpHeaders.setTransferEncodingChunked(httpResponse)
    }

    override val close = Interceptable0 {
        completed = true
        (response as NettyApplicationResponse).finalize()
    }

    override fun sendFile(file: File, position: Long, length: Long) {
        response.stream {
            if (this is NettyAsyncStream) {
                writeFile(file, position, length)
            } else {
                file.inputStream().use { it.copyTo(this) }
            }
        }
    }

    override fun sendAsyncChannel(channel: AsynchronousByteChannel) {
        response.stream {
            if (this is NettyAsyncStream) {
                writeAsyncChannel(channel)
            } else {
                Channels.newInputStream(channel).use { it.copyTo(this) }
            }
        }
    }

    override fun sendStream(stream: InputStream) {
        response.stream {
            if (this is NettyAsyncStream) {
                writeStream(stream)
            } else {
                stream.use { it.copyTo(this) }
            }
        }
    }
}