package io.ktor.server.netty.cio

import io.ktor.cio.*
import io.ktor.http.*
import io.ktor.server.netty.*
import io.netty.buffer.*
import io.netty.channel.*
import io.netty.handler.codec.http.*
import io.netty.handler.codec.http2.*
import kotlinx.coroutines.experimental.*
import kotlinx.coroutines.experimental.CancellationException
import kotlinx.coroutines.experimental.channels.*
import kotlinx.coroutines.experimental.io.*
import kotlinx.coroutines.experimental.io.ByteReadChannel.*
import java.io.*
import java.util.*

private const val UNFLUSHED_LIMIT = 65536

internal class NettyResponsePipeline(private val dst: ChannelHandlerContext,
                                     initialEncapsulation: WriterEncapsulation,
                                     private val requestQueue: NettyRequestQueue
) {
    private val readyQueueSize = requestQueue.readLimit
    private val runningQueueSize = requestQueue.runningLimit

    private val incoming: ReceiveChannel<NettyRequestQueue.CallElement> = requestQueue.elements
    private val ready = ArrayDeque<NettyRequestQueue.CallElement>(readyQueueSize)
    private val running = ArrayDeque<NettyRequestQueue.CallElement>(runningQueueSize)

    private val responses = launch(dst.executor().asCoroutineDispatcher() + ResponsePipelineCoroutineName, start = CoroutineStart.UNDISPATCHED) {
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
        while (isNotFull()) {
            if (!pollReady()) {
                tryStart()
                dst.read()
                break
            }

            tryStart()
        }
    }

    private suspend fun fillSuspend() {
        if (running.isEmpty()) {
            val e = incoming.receiveOrNull()

            if (e != null && e.ensureRunning()) {
                running.addLast(e)
                tryFill()
            }
        }
    }

    private fun pollReady(): Boolean {
        for (index in 1..(readyQueueSize - ready.size)) {
            val e = incoming.poll() ?: return false
            ready.addLast(e)
        }
        return true
    }

    private fun tryStart() {
        while (ready.isNotEmpty() && running.size < runningQueueSize) {
            val e = ready.removeFirst()
            if (e.ensureRunning()) {
                running.addLast(e)
            } else {
                break
            }
        }
    }

    private fun isNotFull(): Boolean = ready.size < readyQueueSize || running.size < runningQueueSize

    private fun hasNextResponseMessage(): Boolean {
        tryFill()
        return running.isNotEmpty() && running.peekFirst().call.response.responseMessage.isCompleted
    }

    @Suppress("NOTHING_TO_INLINE")
    private suspend inline fun processElement(element: NettyRequestQueue.CallElement) {
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

    @Suppress("NOTHING_TO_INLINE")
    private suspend inline fun finishCall(call: NettyApplicationCall, lastMessage: Any?) {
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

    @Suppress("NOTHING_TO_INLINE")
    private suspend inline fun processCall(call: NettyApplicationCall) {
        val responseMessage = call.response.responseMessage.await()
        val response = call.response

        if (response.isUpgradeResponse()) {
            processUpgrade(responseMessage)
        } else {
            dst.write(responseMessage)
        }

        tryFill()

        if (responseMessage is FullHttpResponse) {
            return finishCall(call, null)
        }

        val responseChannel = response.responseChannel
        val knownSize = when {
            responseChannel === ByteReadChannel.Empty -> 0
            responseMessage is HttpResponse -> responseMessage.headers().getInt("Content-Length", -1)
            else -> -1
        }

        when (knownSize) {
            0 -> processEmpty(call)
            in 1..65536 -> processSmallContent(call, response, knownSize)
            -1 -> processBodyFlusher(call, response)
            else -> processBodyGeneral(call, response)
        }
    }

    private suspend fun processEmpty(call: NettyApplicationCall) {
        return finishCall(call, encapsulation.endOfStream(false))
    }

    private suspend fun processSmallContent(call: NettyApplicationCall, response: NettyApplicationResponse, size: Int) {
        val buffer = dst.alloc().buffer(size)
        val channel = response.responseChannel

        val start = buffer.writerIndex()
        channel.readFully(buffer.nioBuffer(start, buffer.writableBytes()))
        buffer.writerIndex(start + size)

        val encapsulation = encapsulation
        dst.write(encapsulation.transform(buffer, true))
        finishCall(call, encapsulation.endOfStream(true))
    }

    private suspend fun processBodyGeneral(call: NettyApplicationCall, response: NettyApplicationResponse) {
        val channel = response.responseChannel
        val encapsulation = encapsulation

        var unflushedBytes = 0

        channel.lookAheadSuspend {
            while (true) {
                val buffer = request(0, 1)
                if (buffer == null) {
                    if (!awaitAtLeast(1)) break
                    continue
                }

                val rc = buffer.remaining()
                val buf = dst.alloc().buffer(rc)
                val idx = buf.writerIndex()
                buf.setBytes(idx, buffer)
                buf.writerIndex(idx + rc)

                consumed(rc)
                unflushedBytes += rc

                val message = encapsulation.transform(buf, false)

                if (unflushedBytes >= UNFLUSHED_LIMIT) {
                    tryFill()
                    dst.writeAndFlush(message).suspendAwait()
                    unflushedBytes = 0
                } else {
                    dst.write(message)
                }
            }
        }

        finishCall(call, encapsulation.endOfStream(false))
    }

    private suspend fun processBodyFlusher(call: NettyApplicationCall, response: NettyApplicationResponse) {
        val channel = response.responseChannel
        val encapsulation = encapsulation

        var unflushedBytes = 0

        channel.lookAheadSuspend {
            while (true) {
                val buffer = request(0, 1)
                if (buffer == null) {
                    if (!awaitAtLeast(1)) break
                    continue
                }

                val rc = buffer.remaining()
                val buf = dst.alloc().buffer(rc)
                val idx = buf.writerIndex()
                buf.setBytes(idx, buffer)
                buf.writerIndex(idx + rc)

                consumed(rc)
                unflushedBytes += rc

                val message = encapsulation.transform(buf, false)

                if (unflushedBytes >= UNFLUSHED_LIMIT || channel.availableForRead == 0) {
                    tryFill()
                    dst.writeAndFlush(message).suspendAwait()
                    unflushedBytes = 0
                } else {
                    dst.write(message)
                }
            }
        }

        finishCall(call, encapsulation.endOfStream(false))
    }
}

private fun NettyApplicationResponse.isUpgradeResponse() =
        status()?.value == HttpStatusCode.SwitchingProtocols.value

private val ResponsePipelineCoroutineName = CoroutineName("response-pipeline")

sealed class WriterEncapsulation {
    open val requiresContextClose: Boolean get() = true
    abstract fun transform(buf: ByteBuf, last: Boolean): Any
    abstract fun endOfStream(lastTransformed: Boolean): Any?
    abstract fun upgrade(dst: ChannelHandlerContext)

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
