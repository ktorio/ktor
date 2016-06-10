package org.jetbrains.ktor.netty

import io.netty.bootstrap.*
import io.netty.channel.*
import io.netty.channel.nio.*
import io.netty.channel.socket.*
import io.netty.channel.socket.nio.*
import io.netty.handler.codec.http.*
import io.netty.handler.stream.*
import io.netty.handler.timeout.*
import org.jetbrains.ktor.application.*
import org.jetbrains.ktor.content.*
import org.jetbrains.ktor.host.*
import org.jetbrains.ktor.http.*
import org.jetbrains.ktor.http.HttpHeaders
import org.jetbrains.ktor.pipeline.*
import org.jetbrains.ktor.util.*
import java.util.concurrent.*

/**
 * [ApplicationHost] implementation for running standalone Netty Host
 */
class NettyApplicationHost(override val hostConfig: ApplicationHostConfig,
                           val environment: ApplicationEnvironment,
                           val applicationLifecycle: ApplicationLifecycle) : ApplicationHost {

    private val application: Application get() = applicationLifecycle.application

    constructor(hostConfig: ApplicationHostConfig, environment: ApplicationEnvironment)
    : this(hostConfig, environment, ApplicationLoader(environment, hostConfig.autoreload))

    constructor(hostConfig: ApplicationHostConfig, environment: ApplicationEnvironment, application: Application)
    : this(hostConfig, environment, object : ApplicationLifecycle {
        override val application: Application = application
        override fun dispose() {
        }
    })

    private val mainEventGroup = NioEventLoopGroup()
    private val workerEventGroup = NioEventLoopGroup()
    private val executorGroup = NioEventLoopGroup()

    private val bootstrap = ServerBootstrap().apply {
        group(mainEventGroup, workerEventGroup)
        channel(NioServerSocketChannel::class.java)
        childHandler(object : ChannelInitializer<SocketChannel>() {
            override fun initChannel(ch: SocketChannel) {
                with (ch.pipeline()) {
                    addLast(HttpServerCodec())
                    addLast(ChunkedWriteHandler())
                    addLast(WriteTimeoutHandler(10))
                    addLast(HostHttpHandler())
                }
            }
        })

    }

    override val executor: Executor
        get() = executorGroup

    override fun start(wait: Boolean) {
        environment.log.info("Starting server...")
        val channelFuture = bootstrap.bind(hostConfig.host, hostConfig.port).sync()
        environment.log.info("Server running.")
        if (wait) {
            channelFuture.channel().closeFuture().sync()
            applicationLifecycle.dispose()
            environment.log.info("Server stopped.")
        }
    }

    override fun stop() {
        workerEventGroup.shutdownGracefully()
        mainEventGroup.shutdownGracefully()
        applicationLifecycle.dispose()
        environment.log.info("Server stopped.")
    }

    inner class HostHttpHandler : SimpleChannelInboundHandler<HttpRequest>(false) {
        override fun channelRead0(context: ChannelHandlerContext, request: HttpRequest) {
            val requestContentType = request.headers().get(HttpHeaders.ContentType)?.let { ContentType.parse(it) }

            if (requestContentType != null && requestContentType.match(ContentType.Application.FormUrlEncoded)) {
                context.channel().config().isAutoRead = true
                val urlEncodedHandler = FormUrlEncodedHandler(Charsets.UTF_8, { parameters ->
                    startHandleRequest(context, request, true, { parameters }, null)
                })
                context.pipeline().addLast(urlEncodedHandler)
            } else {
                context.channel().config().isAutoRead = false
                val dropsHandler = LastDropsCollectorHandler() // in spite of that we have cleared auto-read mode we still need to collect remaining events
                context.pipeline().addLast(dropsHandler)

                startHandleRequest(context, request, false, { ValuesMap.Empty }, dropsHandler)
            }
        }

        override fun exceptionCaught(ctx: ChannelHandlerContext, cause: Throwable) {
            environment.log.error("Application ${application.javaClass} cannot fulfill the request", cause);
            ctx.close()
        }

        override fun channelReadComplete(context: ChannelHandlerContext) {
            context.flush()
        }

        private fun startHandleRequest(context: ChannelHandlerContext, request: HttpRequest, bodyConsumed: Boolean, urlEncodedParameters: () -> ValuesMap, drops: LastDropsCollectorHandler?) {
            val call = NettyApplicationCall(application, context, request, bodyConsumed, urlEncodedParameters, drops, executor)

            setupUpgradeHelper(call, context, drops)

            call.executeOn(executor, application).whenComplete { pipelineState, throwable ->
                val response: Any? = if (throwable != null && !call.completed) {
                    application.environment.log.error("Failed to process request", throwable)
                    HttpStatusCode.InternalServerError
                } else if (pipelineState != PipelineState.Executing && !call.completed) {
                    HttpStatusContent(HttpStatusCode.NotFound, "Cannot find resource with the requested URI: ${request.uri}")
                } else {
                    null
                }

                if (response != null) {
                    call.executionMachine.runBlockWithResult {
                        call.respond(response)
                    }
                }
            }
        }
    }
}

