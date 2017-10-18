package io.ktor.netty.cio

import io.ktor.http.*
import io.ktor.netty.*
import io.netty.buffer.*
import io.netty.channel.*
import io.netty.handler.codec.http.*
import io.netty.handler.codec.http2.*
import kotlinx.coroutines.experimental.*

internal class NettyResponsePipeline(private val dst: ChannelHandlerContext,
                                     initialEncapsulation: WriterEncapsulation,
                                     private val requestQueue: NettyRequestQueue
) {
    @Volatile
    private var cancellation: Throwable? = null
    private val responses = launch(Unconfined, start = CoroutineStart.LAZY) {
        loop()
    }

    private var encapsulation: WriterEncapsulation = initialEncapsulation

    fun ensureRunning() {
        responses.start()
    }

    fun close() {
        responses.cancel()
    }

    fun cancel(cause: Throwable) {
        cancellation = cause
        responses.cancel()
    }

    private suspend fun loop() {
        var cancellationReported = false
        while (true) {
            val call = requestQueue.receiveOrNull() ?: break
            try {
                cancellation?.let { throw it }

                processCall(call)
            } catch (t: Throwable) {
                call.dispose()
                call.responseWriteJob.cancel(t)
                cancel(t)
                requestQueue.cancel()

                if (!cancellationReported) {
                    cancellationReported = true
                    dst.fireExceptionCaught(t)
                }
            } finally {
                call.responseWriteJob.cancel()
            }
        }

        dst.close()
    }

    private suspend fun processCall(call: NettyApplicationCall) {
        val response = call.response
        val responseMessage = response.responseMessage.await()
        val statusCode = response.status()
        val close = !call.request.keepAlive
        val responseMessageFuture = dst.writeAndFlush(responseMessage)

        if (statusCode?.value == HttpStatusCode.SwitchingProtocols.value) {
            responseMessageFuture.suspendAwait()
            encapsulation.upgrade(dst)
            encapsulation = WriterEncapsulation.Raw
        }

        val channel = response.responseChannel

        while (true) {
            val buf = dst.alloc().buffer(4096)
            val bb = buf.nioBuffer(buf.writerIndex(), buf.writableBytes())
            val rc = channel.readAvailable(bb)
            if (rc == -1) {
                buf.release()
                break
            }
            buf.writerIndex(buf.writerIndex() + rc)
            val message = encapsulation.transform(buf)

            dst.writeAndFlush(message).suspendAwait()
        }

        encapsulation.endOfStream()?.let { dst.writeAndFlush(it).suspendAwait() }

        if (close) {
            requestQueue.cancel()
        }
    }
}

sealed class WriterEncapsulation {
    abstract fun transform(buf: ByteBuf): Any
    abstract fun endOfStream(): Any?
    abstract fun upgrade(dst: ChannelHandlerContext): Unit

    object Http1 : WriterEncapsulation() {
        override fun transform(buf: ByteBuf): Any {
            return DefaultHttpContent(buf)
        }

        override fun endOfStream(): Any? {
            return LastHttpContent.EMPTY_LAST_CONTENT
        }

        override fun upgrade(dst: ChannelHandlerContext) {
            dst.pipeline().apply {
                remove(HttpServerCodec::class.java)
                addFirst(NettyDirectEncoder())
            }
        }
    }

    object Http2 : WriterEncapsulation() {
        override fun transform(buf: ByteBuf): Any {
            return DefaultHttp2DataFrame(buf, false)
        }

        override fun endOfStream(): Any? {
            return DefaultHttp2DataFrame(true)
        }

        override fun upgrade(dst: ChannelHandlerContext) {
            throw IllegalStateException("HTTP/2 doesn't support upgrade")
        }
    }

    object Raw : WriterEncapsulation() {
        override fun transform(buf: ByteBuf): Any {
            return buf
        }

        override fun endOfStream(): Any? {
            return null
        }

        override fun upgrade(dst: ChannelHandlerContext) {
            throw IllegalStateException("Already upgraded")
        }
    }
}
