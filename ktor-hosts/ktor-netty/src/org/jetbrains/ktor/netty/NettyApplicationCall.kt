package org.jetbrains.ktor.netty

import io.netty.buffer.*
import io.netty.channel.*
import io.netty.handler.codec.http.*
import org.jetbrains.ktor.application.*
import org.jetbrains.ktor.cio.*
import org.jetbrains.ktor.content.*
import org.jetbrains.ktor.host.*
import org.jetbrains.ktor.pipeline.*
import java.util.concurrent.atomic.*

internal class NettyApplicationCall(application: Application,
                                    val context: ChannelHandlerContext,
                                    val httpRequest: HttpRequest,
                                    contentQueue: NettyContentQueue

) : BaseApplicationCall(application) {

    var completed: Boolean = false

    private val closed = AtomicBoolean(false)

    override val request = NettyApplicationRequest(httpRequest, NettyConnectionPoint(httpRequest, context), contentQueue)
    override val response = NettyApplicationResponse(this, respondPipeline, context)

    class NettyBufferTicket(val bb: ByteBuf) : ReleasablePoolTicket(bb.nioBuffer(0, bb.capacity()))
    override val bufferPool = object : ByteBufferPool {
        private val allocator = context.alloc()

        override fun allocate(size: Int): PoolTicket {
            val heapBuffer = allocator.heapBuffer(size)
            return NettyBufferTicket(heapBuffer)
        }

        override fun release(buffer: PoolTicket) {
            val ticket = buffer as NettyBufferTicket
            ticket.bb.release()
            ticket.release()
        }
    }

    suspend override fun respond(message: Any) {
        super.respond(message)

        completed = true
        response.close()
        request.close()

        if (closed.compareAndSet(false, true)) {
            val finishContent = context.writeAndFlush(LastHttpContent.EMPTY_LAST_CONTENT)
            if (!HttpUtil.isKeepAlive(httpRequest)) {
                // close channel if keep-alive was not requested
                finishContent.addListener(ChannelFutureListener.CLOSE)
            } else {
                // reenable read operations on a channel if keep-alive was requested
                finishContent.addListener {
                    // remove finished content queue, handler will install new
                    // TODO: change it to shareable context-agnostic concurrent map
                    context.pipeline().remove(HttpContentQueue::class.java)

                    context.channel().config().isAutoRead = true
                    context.read()
                }
            }
        }
    }

    override fun PipelineContext<*>.handleUpgrade(upgrade: ProtocolUpgrade) {
/*
        context.executeInLoop {
            context.channel().pipeline().remove(ChunkedWriteHandler::class.java)
            context.channel().pipeline().remove(HostHttpHandler::class.java)

            response.status(upgrade.status ?: HttpStatusCode.SwitchingProtocols)
            upgrade.headers.flattenEntries().forEach { e ->
                response.headers.append(e.first, e.second)
            }

            context.channel().pipeline().addFirst(NettyDirectDecoder())

            context.writeAndFlush(httpResponse).addListener {
                context.channel().pipeline().remove(HttpServerCodec::class.java)
                context.channel().pipeline().addFirst(NettyDirectEncoder())

                upgrade.upgrade(this@NettyApplicationCall, this, request.content.get(), responseChannel())
            }
        }
*/
    }

    override fun responseChannel(): WriteChannel = response.writeChannel.value
}