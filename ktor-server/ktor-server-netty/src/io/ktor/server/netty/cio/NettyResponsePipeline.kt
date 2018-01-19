package io.ktor.server.netty.cio

import io.ktor.cio.*
import io.ktor.http.*
import io.ktor.server.netty.*
import io.netty.buffer.*
import io.netty.channel.*
import io.netty.handler.codec.http.*
import io.netty.handler.codec.http2.*
import kotlinx.coroutines.experimental.*
import java.io.*

internal class NettyResponsePipeline(private val dst: ChannelHandlerContext,
                                     initialEncapsulation: WriterEncapsulation,
                                     private val requestQueue: NettyRequestQueue
) {
    @Volatile
    private var cancellation: Throwable? = null
    private val responses = launch(dst.executor().asCoroutineDispatcher() + ResponsePipelineCoroutineName, start = CoroutineStart.LAZY) {
        try {
            loop()
        } catch (t: Throwable) {
            if (t !is CancellationException) {
                dst.fireExceptionCaught(t)
            }

            dst.close()
        }
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
        while (true) {
            val call = requestQueue.receiveOrNull() ?: break
            try {
                cancellation?.let { throw it }

                processCall(call)
            } catch (actualException: Throwable) {
                val t = when {
                    actualException is IOException && actualException !is ChannelIOException -> ChannelWriteException(exception = actualException)
                    else -> actualException
                }
                call.dispose()
                call.responseWriteJob.cancel(t)
                cancel(t)
                requestQueue.cancel()
            } finally {
                call.responseWriteJob.cancel()
            }
        }

        if (encapsulation.requiresContextClose) {
            dst.close()
        }
    }

    private suspend fun processCall(call: NettyApplicationCall) {
        val response = call.response
        val responseMessage = response.responseMessage.await()
        val close = !call.request.keepAlive

        val upgradeResponse = response.status()?.value == HttpStatusCode.SwitchingProtocols.value
        val channel = response.responseChannel

        if (upgradeResponse) {
            dst.write(responseMessage)
            encapsulation.upgrade(dst)
            encapsulation = WriterEncapsulation.Raw
            dst.flush()
        } else if (channel.availableForRead > 0) {
            dst.write(responseMessage)
        } else {
            dst.writeAndFlush(responseMessage)
        }

        var unflushedBytes = 0
        var last = false
        while (true) {
            val buf = dst.alloc().buffer(4096)
            val bb = buf.nioBuffer(buf.writerIndex(), buf.writableBytes())
            val rc = channel.readAvailable(bb)
            if (rc == -1) {
                buf.release()
                break
            }
            buf.writerIndex(buf.writerIndex() + rc)
            unflushedBytes += rc
            val available = channel.availableForRead

            last = available == 0 && channel.isClosedForRead
            val message = encapsulation.transform(buf, last)

            if (available == 0 || unflushedBytes >= UnflushedLimit) {
                dst.writeAndFlush(message).suspendAwait()
                unflushedBytes = 0
            } else {
                dst.write(message)
            }
        }

        encapsulation.endOfStream(last)?.let { dst.writeAndFlush(it).suspendAwait() }

        if (close) {
            requestQueue.cancel()
        }
    }
}

private val ResponsePipelineCoroutineName = CoroutineName("response-pipeline")
private const val UnflushedLimit = 65536

sealed class WriterEncapsulation {
    open val requiresContextClose: Boolean get() = true
    abstract fun transform(buf: ByteBuf, last: Boolean): Any
    abstract fun endOfStream(lastTransformed: Boolean): Any?
    abstract fun upgrade(dst: ChannelHandlerContext): Unit

    object Http1 : WriterEncapsulation() {
        override fun transform(buf: ByteBuf, last: Boolean): Any {
            return DefaultHttpContent(buf)
        }

        override fun endOfStream(lastTransformed: Boolean): Any? {
            return LastHttpContent.EMPTY_LAST_CONTENT
        }

        override fun upgrade(dst: ChannelHandlerContext) {
            dst.pipeline().apply {
                replace(HttpServerCodec::class.java, "direct-encoder", NettyDirectEncoder())
            }
        }
    }

    object Http2 : WriterEncapsulation() {
        override val requiresContextClose: Boolean get() = false

        override fun transform(buf: ByteBuf, last: Boolean): Any {
            return DefaultHttp2DataFrame(buf, last)
        }

        override fun endOfStream(lastTransformed: Boolean): Any? {
            return if (lastTransformed) null else DefaultHttp2DataFrame(true)
        }

        override fun upgrade(dst: ChannelHandlerContext) {
            throw IllegalStateException("HTTP/2 doesn't support upgrade")
        }
    }

    object Raw : WriterEncapsulation() {
        override fun transform(buf: ByteBuf, last: Boolean): Any {
            return buf
        }

        override fun endOfStream(lastTransformed: Boolean): Any? {
            return null
        }

        override fun upgrade(dst: ChannelHandlerContext) {
            throw IllegalStateException("Already upgraded")
        }
    }
}
