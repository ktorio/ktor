package org.jetbrains.ktor.netty.http2

import io.netty.buffer.*
import io.netty.channel.*
import io.netty.handler.codec.http2.*
import io.netty.util.concurrent.*
import org.jetbrains.ktor.cio.*
import java.nio.*
import java.nio.channels.*
import java.util.concurrent.atomic.*
import kotlin.coroutines.experimental.*

internal class NettyHttp2WriteChannel(val context: ChannelHandlerContext) : WriteChannel {
    @Volatile
    private var lastPromise: ChannelPromise? = null

    private val currentHandler = AtomicReference<Continuation<Unit>?>()
    private var currentBuffer: ByteBuffer? = null

    private val closed = AtomicBoolean()

    private val writeFutureListener = GenericFutureListener<Future<Void>> { f ->
        try {
            f.get()

            val buffer = currentBuffer
            currentBuffer = null
            val handler = currentHandler.getAndSet(null)

            if (handler != null && buffer != null) {
                buffer.position(buffer.limit())
                handler.resume(Unit)
            }
        } catch (t: Throwable) {
            val buffer = currentBuffer
            currentBuffer = null
            val handler = currentHandler.getAndSet(null)

            if (handler != null && buffer != null) {
                handler.resumeWithException(t)
            }
        }
    }

    override suspend fun flush() {
        return suspendCoroutine { handler ->
            lastPromise?.addListener { f ->
                try {
                    f.get()
                    handler.resume(Unit)
                } catch (t: Throwable) {
                    handler.resumeWithException(t)
                }
            } ?: run { handler.resume(Unit) }
        }
    }

    override fun close() {
        if (closed.compareAndSet(false, true)) {
            fun sendEnd() {
                context.onLoop {
                    context.writeAndFlush(DefaultHttp2DataFrame(true))
                }
            }

            lastPromise?.addListener { sendEnd() } ?: run { sendEnd() }
        }
    }

    override suspend fun write(src: ByteBuffer) {
        return suspendCoroutine { continuation ->
            if (!currentHandler.compareAndSet(null, continuation)) {
                throw IllegalStateException("write operation is already in progress")
            }
            if (closed.get()) {
                currentHandler.set(null)
                continuation.resumeWithException(ClosedChannelException())
            } else {
                currentBuffer = src

                context.onLoop {
                    val data = Unpooled.wrappedBuffer(currentBuffer!!)

                    val promise = context.channel().newPromise()
                    lastPromise = promise

                    context.writeAndFlush(DefaultHttp2DataFrame(data, false), promise)

                    promise.addListener(writeFutureListener)
                }
            }
        }
    }

    private inline fun ChannelHandlerContext.onLoop(crossinline block: () -> Unit) {
        if (channel().eventLoop().inEventLoop()) {
            block()
        } else {
            channel().eventLoop().execute {
                block()
            }
        }
    }
}