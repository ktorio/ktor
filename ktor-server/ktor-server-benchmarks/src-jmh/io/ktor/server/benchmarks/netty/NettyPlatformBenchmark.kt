package io.ktor.server.benchmarks.netty

import io.ktor.server.benchmarks.*
import io.netty.bootstrap.*
import io.netty.buffer.*
import io.netty.channel.*
import io.netty.channel.nio.*
import io.netty.channel.socket.*
import io.netty.channel.socket.nio.*
import io.netty.handler.codec.http.*
import io.netty.util.*
import java.net.*
import java.util.concurrent.*

class NettyPlatformBenchmark : PlatformBenchmark() {
    private lateinit var channel: Channel
    val eventLoopGroup = NioEventLoopGroup()

    override fun runServer(port: Int) {
        ResourceLeakDetector.setLevel(ResourceLeakDetector.Level.DISABLED)

        val inet = InetSocketAddress(port)
        val b = ServerBootstrap()

        b.option(ChannelOption.SO_BACKLOG, 8192)
        b.option(ChannelOption.SO_REUSEADDR, true)
        b.group(eventLoopGroup).channel(NioServerSocketChannel::class.java).childHandler(BenchmarkServerInitializer(eventLoopGroup.next()))
        b.childOption(ChannelOption.SO_REUSEADDR, true)

        channel = b.bind(inet).sync().channel()
    }

    override fun stopServer() {
        eventLoopGroup.shutdownGracefully()
        channel.closeFuture().sync()
    }

    private class BenchmarkServerInitializer(private val service: ScheduledExecutorService) : ChannelInitializer<SocketChannel>() {

        @Throws(Exception::class)
        public override fun initChannel(ch: SocketChannel) {
            ch.pipeline()
                    .addLast("encoder", HttpResponseEncoder())
                    .addLast("decoder", HttpRequestDecoder(4096, 8192, 8192, false))
                    .addLast("handler", BenchmarkServerHandler(service))
        }
    }

    class BenchmarkServerHandler internal constructor(service: ScheduledExecutorService) : ChannelInboundHandlerAdapter() {
        override fun channelRead(ctx: ChannelHandlerContext, msg: Any) {
            when (msg) {
                is HttpRequest -> try {
                    process(ctx, msg)
                } finally {
                    ReferenceCountUtil.release(msg)
                }
                else -> ctx.fireChannelRead(msg)
            }
        }

        @Throws(Exception::class)
        private fun process(ctx: ChannelHandlerContext, request: HttpRequest) {
            val uri = request.uri()
            when (uri) {
                "/sayOK" -> {
                    writePlainResponse(ctx, PLAINTEXT_CONTENT_BUFFER.duplicate())
                    return
                }
                else -> {
                    val response = DefaultFullHttpResponse(HttpVersion.HTTP_1_1, HttpResponseStatus.NOT_FOUND, Unpooled.EMPTY_BUFFER, false)
                    ctx.write(response).addListener(ChannelFutureListener.CLOSE)
                }
            }
        }

        private fun writePlainResponse(ctx: ChannelHandlerContext, buf: ByteBuf) {
            ctx.write(makeResponse(buf, HttpHeaderValues.TEXT_PLAIN, PLAINTEXT_CLHEADER_VALUE), ctx.voidPromise())
        }

        private fun makeResponse(buf: ByteBuf, contentType: CharSequence, contentLength: CharSequence): FullHttpResponse {
            val response = DefaultFullHttpResponse(HttpVersion.HTTP_1_1, HttpResponseStatus.OK, buf, false)
            response.headers()
                    .set(HttpHeaderNames.CONTENT_TYPE, contentType)
                    .set(HttpHeaderNames.CONTENT_LENGTH, contentLength)
            return response
        }

        @Throws(Exception::class)
        override fun exceptionCaught(ctx: ChannelHandlerContext, cause: Throwable) {
            ctx.close()
        }

        @Throws(Exception::class)
        override fun channelReadComplete(ctx: ChannelHandlerContext) {
            ctx.flush()
        }

        companion object {
            private val STATIC_PLAINTEXT = "OK".toByteArray(CharsetUtil.UTF_8)
            private val STATIC_PLAINTEXT_LEN = STATIC_PLAINTEXT.size

            private val PLAINTEXT_CONTENT_BUFFER = Unpooled.unreleasableBuffer(Unpooled.directBuffer().writeBytes(STATIC_PLAINTEXT))
            private val PLAINTEXT_CLHEADER_VALUE = AsciiString(STATIC_PLAINTEXT_LEN.toString())
        }
    }
}