package org.jetbrains.ktor.netty

import io.netty.bootstrap.*
import io.netty.channel.*
import io.netty.channel.nio.*
import io.netty.channel.socket.*
import io.netty.channel.socket.nio.*
import io.netty.handler.codec.http.*
import org.jetbrains.ktor.application.*

public class NettyApplicationHost(val config: ApplicationConfig) {
    private val loader: ApplicationLoader = ApplicationLoader(config)
    val application: Application get() = loader.application

    val mainEventGroup = NioEventLoopGroup()
    val workerEventGroup = NioEventLoopGroup()

    val bootstrap = ServerBootstrap().apply {
        group(mainEventGroup, workerEventGroup)
        channel(NioServerSocketChannel::class.java)
        childHandler(object : ChannelInitializer<SocketChannel>() {
            protected override fun initChannel(ch: SocketChannel) {
                with (ch.pipeline()) {
                    addLast(HttpServerCodec())
                    addLast(HttpObjectAggregator(1048576))
                    addLast(HostHttpHandler())
                }
            }
        })

    }

    public fun start(wait: Boolean = true) {
        val channel = bootstrap.bind(config.port).sync().channel()
        if (wait) {
            channel.closeFuture().sync()
        }
    }

    public fun stop() {
        workerEventGroup.shutdownGracefully()
        mainEventGroup.shutdownGracefully()
        loader.dispose()
    }

    inner class HostHttpHandler : SimpleChannelInboundHandler<FullHttpRequest>() {
        override fun channelRead0(context: ChannelHandlerContext, request: FullHttpRequest) {
            val applicationRequest = NettyApplicationRequestContext(application, context, request)
            val requestResult = application.handle(applicationRequest)
            when (requestResult) {
                ApplicationRequestStatus.Unhandled -> {
                    val notFound = DefaultFullHttpResponse(request.protocolVersion, HttpResponseStatus.NOT_FOUND)
                    notFound.headers().set("Content-Type", "text/html; charset=UTF-8")
                    notFound.content().writeBytes("""
                            <h1>Not Found</h1>
                            Cannot find resource with the requested URI: ${request.uri}
                            """.toByteArray(Charsets.UTF_8))
                    context.writeAndFlush(notFound)
                    applicationRequest.close()
                }
                ApplicationRequestStatus.Handled -> applicationRequest.close()
                ApplicationRequestStatus.Asynchronous -> { /* do nothing */}
            }
        }

        override fun exceptionCaught(ctx: ChannelHandlerContext, cause: Throwable) {
            config.log.error("Application ${application.javaClass} cannot fulfill the request", cause);
            ctx.close()
        }

        override fun channelReadComplete(ctx: ChannelHandlerContext) {
            ctx.flush()
        }
    }
}




