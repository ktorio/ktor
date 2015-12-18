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
import org.jetbrains.ktor.routing.*

public class NettyApplicationHost : ApplicationHost {
    private val applicationLifecycle: ApplicationLifecycle
    private val config: ApplicationConfig
    private val application: Application get() = applicationLifecycle.application

    constructor(config: ApplicationConfig) {
        this.config = config
        this.applicationLifecycle = ApplicationLoader(config)
    }

    constructor(config: ApplicationConfig, applicationFunction: ApplicationLifecycle) {
        this.config = config
        this.applicationLifecycle = applicationFunction
    }

    constructor(config: ApplicationConfig, application: Application) {
        this.config = config
        this.applicationLifecycle = object : ApplicationLifecycle {
            override val application: Application = application
            override fun dispose() {
            }
        }
    }

    private val mainEventGroup = NioEventLoopGroup()
    private val workerEventGroup = NioEventLoopGroup()

    private val bootstrap = ServerBootstrap().apply {
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

    public override fun start() {
        bootstrap.bind(config.port).sync()
        config.log.info("Server running.")
    }

    public fun start(wait: Boolean = true) {
        val channel = bootstrap.bind(config.port).sync().channel()
        config.log.info("Server running.")
        if (wait) {
            channel.closeFuture().sync()
        }
    }

    public override fun stop() {
        workerEventGroup.shutdownGracefully()
        mainEventGroup.shutdownGracefully()
        applicationLifecycle.dispose()
        config.log.info("Server stopped.")
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
                ApplicationRequestStatus.Asynchronous -> {
                    /* do nothing */
                }
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

fun embeddedNettyServer(port: Int, application: Routing.() -> Unit) = embeddedNettyServer(applicationConfig { this.port = port }, application)
fun embeddedNettyServer(config: ApplicationConfig, application: Routing.() -> Unit): ApplicationHost {
    val applicationObject = object : Application(config) {
        init {
            Routing().apply(application).installInto(this)
        }
    }
    return NettyApplicationHost(config, applicationObject)
}


