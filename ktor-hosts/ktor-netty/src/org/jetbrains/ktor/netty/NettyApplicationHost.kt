package org.jetbrains.ktor.netty

import io.netty.bootstrap.*
import io.netty.channel.*
import io.netty.channel.nio.*
import io.netty.channel.socket.*
import io.netty.channel.socket.nio.*
import io.netty.handler.codec.http.*
import io.netty.handler.stream.*
import org.jetbrains.ktor.application.*
import org.jetbrains.ktor.content.*
import org.jetbrains.ktor.host.*
import org.jetbrains.ktor.http.*

/**
 * [ApplicationHost] implementation for running standalone Netty Host
 */
class NettyApplicationHost(override val hostConfig: ApplicationHostConfig,
                           val config: ApplicationConfig,
                           val applicationLifecycle: ApplicationLifecycle) : ApplicationHost {

    private val application: Application get() = applicationLifecycle.application

    constructor(hostConfig: ApplicationHostConfig, config: ApplicationConfig)
    : this(hostConfig, config, ApplicationLoader(config))

    constructor(hostConfig: ApplicationHostConfig, config: ApplicationConfig, application: Application)
    : this(hostConfig, config, object : ApplicationLifecycle {
        override val application: Application = application
        override fun dispose() {
        }
    })

    private val mainEventGroup = NioEventLoopGroup()
    private val workerEventGroup = NioEventLoopGroup()

    private val bootstrap = ServerBootstrap().apply {
        group(mainEventGroup, workerEventGroup)
        channel(NioServerSocketChannel::class.java)
        childHandler(object : ChannelInitializer<SocketChannel>() {
            override fun initChannel(ch: SocketChannel) {
                with (ch.pipeline()) {
                    addLast(HttpServerCodec())
                    addLast(HttpObjectAggregator(1048576))
                    addLast(ChunkedWriteHandler())
                    addLast(HostHttpHandler())
                }
            }
        })

    }

    public override fun start(wait: Boolean) {
        config.log.info("Starting server...")
        val channelFuture = bootstrap.bind(hostConfig.host, hostConfig.port).sync()
        config.log.info("Server running.")
        if (wait) {
            channelFuture.channel().closeFuture().sync()
            applicationLifecycle.dispose()
            config.log.info("Server stopped.")
        }
    }

    override fun stop() {
        workerEventGroup.shutdownGracefully()
        mainEventGroup.shutdownGracefully()
        applicationLifecycle.dispose()
        config.log.info("Server stopped.")
    }

    inner class HostHttpHandler : SimpleChannelInboundHandler<FullHttpRequest>() {
        override fun channelRead0(context: ChannelHandlerContext, request: FullHttpRequest) {
            val call = NettyApplicationCall(application, context, request)
            val pipelineState = application.handle(call)
            if (pipelineState.finished() && !call.completed) {
                val response = HttpStatusContent(HttpStatusCode.NotFound, "Cannot find resource with the requested URI: ${request.uri}")
                call.respond(response)
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

