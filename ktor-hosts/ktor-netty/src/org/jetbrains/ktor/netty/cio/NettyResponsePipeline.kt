package org.jetbrains.ktor.netty.cio

import io.netty.buffer.*
import io.netty.channel.*
import io.netty.handler.codec.http.*
import io.netty.handler.codec.http2.*
import kotlinx.coroutines.experimental.*
import kotlinx.coroutines.experimental.channels.*
import kotlinx.coroutines.experimental.channels.Channel
import org.jetbrains.ktor.http.*
import org.jetbrains.ktor.netty.*

internal class NettyResponsePipeline(private val dst: ChannelHandlerContext, initialEncapsulation: WriterEncapsulation) {
    private val responses = actor<NettyApplicationCall>(Unconfined, capacity = Channel.UNLIMITED, start = CoroutineStart.LAZY) {
        loop()
    }

    private var encapsulation: WriterEncapsulation = initialEncapsulation
    val job: Job get() = responses

    fun send(channel: NettyApplicationCall) {
        if (!responses.offer(channel)) throw IllegalStateException()
    }

    fun close() {
        responses.close()
    }

    private suspend fun ActorScope<NettyApplicationCall>.loop() {
        consumeEach { call ->
            try {
                val response = call.response
                val responseMessage = response.responseMessage.await()
                val statusCode = response.status()
                val close = !call.request.keepAlive

                dst.writeAndFlush(responseMessage).suspendAwait()

                if (statusCode?.value == HttpStatusCode.SwitchingProtocols.value) {
                    encapsulation.upgrade(dst)
                    encapsulation = WriterEncapsulation.Raw
                    responses.close()
                }

                val channel = response.responseChannel

                while (true) {
                    val buf = dst.alloc().buffer(channel.availableForRead.coerceIn(256, 4096))
                    val bb = buf.nioBuffer(buf.writerIndex(), buf.writableBytes())
                    val rc = channel.readAvailable(bb)
                    if (rc == -1) {
                        buf.release()
                        break
                    }
                    buf.writerIndex(buf.writerIndex() + rc)
                    dst.writeAndFlush(encapsulation.transform(buf)).suspendAwait()
                }

                encapsulation.endOfStream()?.let { dst.writeAndFlush(it).suspendAwait() }

                if (close) {
                    responses.close()
                }
            } catch (t: Throwable) {
                dst.fireExceptionCaught(t)
                call.responseWriteJob.cancel(t)
                throw t
            } finally {
                call.responseWriteJob.cancel()
            }
        }

        dst.close()
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
