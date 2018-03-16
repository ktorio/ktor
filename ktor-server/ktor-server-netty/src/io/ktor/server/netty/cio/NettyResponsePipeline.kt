package io.ktor.server.netty.cio

import io.ktor.cio.*
import io.ktor.http.*
import io.ktor.server.netty.*
import io.netty.buffer.*
import io.netty.channel.*
import io.netty.handler.codec.http.*
import io.netty.handler.codec.http2.*
import kotlinx.coroutines.experimental.*
import kotlinx.coroutines.experimental.channels.*
import java.io.*
import java.util.*

internal class NettyResponsePipeline(private val dst: ChannelHandlerContext,
                                     initialEncapsulation: WriterEncapsulation,
                                     private val requestQueue: NettyRequestQueue
) {
    private val incoming: ReceiveChannel<NettyRequestQueue.CallElement> = requestQueue.elements
    private val running = ArrayDeque<NettyRequestQueue.CallElement>(3)

    private val responses = launch(dst.executor().asCoroutineDispatcher() + ResponsePipelineCoroutineName, start = CoroutineStart.LAZY) {
        try {
            processJobs()
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

    private suspend fun processJobs() {
        while (true) {
            fill()
            if (running.isEmpty()) break
            processElement(running.removeFirst())
        }

        if (encapsulation.requiresContextClose) {
            dst.close()
        }
    }

    private suspend fun fill() {
        tryFill()
        if (running.isEmpty()) {
            return fillSuspend()
        }
    }

    private fun tryFill() {
        while (running.isNotFull()) {
            val e = incoming.poll() ?: break

            if (e.ensureRunning()) {
                running.addLast(e)
            } else {
                break
            }
        }
    }

    private suspend fun fillSuspend() {
        if (running.isEmpty()) {
            val e = incoming.receiveOrNull()

            if (e != null && e.ensureRunning()) {
                running.add(e)
            }
        }
    }

    private fun hasNextResponseMessage(): Boolean {
        tryFill()
        return running.isNotEmpty() && running.peekFirst().call.response.responseMessage.isCompleted
    }

    private suspend fun processElement(element: NettyRequestQueue.CallElement) {
        val call = element.call

        try {
            processCall(call)
        } catch (actualException: Throwable) {
            val t = when {
                actualException is IOException && actualException !is ChannelIOException -> ChannelWriteException(exception = actualException)
                else -> actualException
            }

            call.response.responseChannel.cancel(t)
            call.responseWriteJob.cancel(t)
            call.response.cancel()
            call.dispose()
            responses.cancel()
            requestQueue.cancel()
        } finally {
            call.responseWriteJob.cancel()
        }
    }

    private fun processUpgrade(responseMessage: Any) {
        dst.write(responseMessage)
        encapsulation.upgrade(dst)
        encapsulation = WriterEncapsulation.Raw
        dst.flush()
    }

    private suspend fun finishCall(call: NettyApplicationCall, lastMessage: Any?) {
        val close = !call.request.keepAlive
        val doNotFlush = hasNextResponseMessage() && !close

        val f: ChannelFuture? = when {
            lastMessage == null && doNotFlush -> null
            lastMessage == null -> {
                dst.flush()
                null
            }
            doNotFlush -> {
                dst.write(lastMessage)
                null
            }
            else -> dst.writeAndFlush(lastMessage)
        }

        f?.suspendAwait()

        if (close) {
            requestQueue.cancel()
            dst.flush()
        }
    }

    private suspend fun processCall(call: NettyApplicationCall) {
        val responseMessage = call.response.responseMessage.await()
        val response = call.response

        if (response.isUpgradeResponse()) {
            processUpgrade(responseMessage)
//        } else if (channel.availableForRead > 0 || requestQueue.hasNextResponseMessage()) {
        } else {
            dst.write(responseMessage)
//            dst.writeAndFlush(responseMessage)
        }

        tryFill()

        val channel = response.responseChannel
        val encapsulation = encapsulation

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

            last = channel.isClosedForRead
            val message = encapsulation.transform(buf, last)

            if (unflushedBytes >= UnflushedLimit) {
                tryFill()
                dst.writeAndFlush(message).suspendAwait()
                unflushedBytes = 0
            } else {
                dst.write(message)
            }

            if (last) break
        }

        val lastMessage = encapsulation.endOfStream(last)
        finishCall(call, lastMessage)
    }
}

private fun NettyApplicationResponse.isUpgradeResponse() =
        status()?.value == HttpStatusCode.SwitchingProtocols.value

private fun <E> ArrayDeque<E>.isNotFull(): Boolean = size != 3


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
