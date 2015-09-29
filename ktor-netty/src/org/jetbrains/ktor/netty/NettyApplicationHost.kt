package org.jetbrains.ktor.netty

import io.netty.bootstrap.*
import io.netty.channel.*
import io.netty.channel.nio.*
import io.netty.channel.socket.*
import io.netty.channel.socket.nio.*
import io.netty.handler.codec.http.*
import org.jetbrains.ktor.application.*
import org.jetbrains.ktor.content.*
import org.jetbrains.ktor.http.*

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
            val applicationContext = NettyApplicationRequestContext(application, context, request)
            val requestResult = application.handle(applicationContext)
            when (requestResult) {
                ApplicationRequestStatus.Unhandled -> {
                    val response = TextErrorContent(HttpStatusCode.NotFound, "Cannot find resource with the requested URI: ${request.uri}")
                    applicationContext.response.send(response)
                    applicationContext.close()
                }
                ApplicationRequestStatus.Handled -> applicationContext.close()
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




