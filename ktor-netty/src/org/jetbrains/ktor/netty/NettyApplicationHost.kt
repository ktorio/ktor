package org.jetbrains.ktor.netty

import io.netty.bootstrap.*
import io.netty.channel.*
import io.netty.channel.nio.*
import io.netty.channel.socket.*
import io.netty.channel.socket.nio.*
import io.netty.handler.codec.http.*
import io.netty.handler.logging.*
import io.netty.handler.stream.*
import org.jetbrains.ktor.application.*

public class NettyApplicationHost(val config: ApplicationConfig) {
    private val loader: ApplicationLoader = ApplicationLoader(config)
    val application: Application get() = loader.application

    val mainEventGroup = NioEventLoopGroup()
    val workerEventGroup = NioEventLoopGroup()

    val bootstrap = ServerBootstrap().apply {
        group(mainEventGroup, workerEventGroup)
        channel(javaClass<NioServerSocketChannel>())
        childHandler(object : ChannelInitializer<SocketChannel>() {
            protected override fun initChannel(ch: SocketChannel) {
                with (ch.pipeline()) {
                    addLast(HttpServerCodec())
                    addLast(HttpObjectAggregator(1048576))
                    addLast(LoggingHandler(LogLevel.DEBUG))
                    addLast(object : SimpleChannelInboundHandler<Any>() {
                        override fun channelRead0(context: ChannelHandlerContext, request: Any) {
                            when(request) {
                                is HttpRequest -> application.handle(NettyApplicationRequest(application, context, request))
                            }
                        }

                        override fun exceptionCaught(ctx: ChannelHandlerContext, cause: Throwable) {
                            config.log.error(cause)
                            ctx.close()
                        }

                        override fun channelReadComplete(ctx: ChannelHandlerContext) {
                            ctx.flush()

                        }
                    })
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
}



