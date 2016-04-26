package org.jetbrains.ktor.netty

import io.netty.bootstrap.*
import io.netty.channel.*
import io.netty.channel.nio.*
import io.netty.channel.socket.*
import io.netty.channel.socket.nio.*
import io.netty.handler.codec.http.*
import io.netty.handler.stream.*
import io.netty.util.AttributeKey
import org.jetbrains.ktor.application.*
import org.jetbrains.ktor.content.*
import org.jetbrains.ktor.host.*
import org.jetbrains.ktor.http.*
import org.jetbrains.ktor.http.HttpHeaders
import org.jetbrains.ktor.pipeline.*
import org.jetbrains.ktor.util.*

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
                    addLast(ChunkedWriteHandler())
                    addLast(HostHttpHandler())
                }
            }
        })

    }

    override fun start(wait: Boolean) {
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

    private class DelayedHandleState(val request: HttpRequest, val bodyConsumed: Boolean, val urlEncodedParameters: () -> ValuesMap)

    inner class HostHttpHandler : SimpleChannelInboundHandler<HttpRequest>(false) {
        override fun channelRead0(context: ChannelHandlerContext, request: HttpRequest) {
            val requestContentType = request.headers().get(HttpHeaders.ContentType)?.let { ContentType.parse(it) }

            if (requestContentType != null && requestContentType.match(ContentType.Application.FormUrlEncoded)) {
                val urlEncodedHandler = FormUrlEncodedHandler(Charsets.UTF_8)
                context.pipeline().addLast(urlEncodedHandler)

                context.attr(DelayedStateAttribute).set(DelayedHandleState(request, true, { urlEncodedHandler.values }))
            } else {
                context.channel().config().isAutoRead = false
                val dropsHandler = LastDropsCollectorHandler() // in spite of that we have cleared auto-read mode we still need to collect remaining events
                context.pipeline().addLast(dropsHandler)

                startHandleRequest(context, request, false, { ValuesMap.Empty }, dropsHandler)
            }
        }

        override fun exceptionCaught(ctx: ChannelHandlerContext, cause: Throwable) {
            config.log.error("Application ${application.javaClass} cannot fulfill the request", cause);
            ctx.close()
        }

        override fun channelReadComplete(context: ChannelHandlerContext) {
            context.flush()

            val state = context.attr(DelayedStateAttribute)?.getAndRemove()
            if (state != null) {
                startHandleRequest(context, state.request, state.bodyConsumed, state.urlEncodedParameters, null)
            }
        }

        private fun startHandleRequest(context: ChannelHandlerContext, request: HttpRequest, bodyConsumed: Boolean, urlEncodedParameters: () -> ValuesMap, drops: LastDropsCollectorHandler?) {
            val call = NettyApplicationCall(application, context, request, bodyConsumed, urlEncodedParameters, drops)
            val pipelineState = call.execute(application)
            if (pipelineState != PipelineState.Executing && !call.completed) {
                val response = HttpStatusContent(HttpStatusCode.NotFound, "Cannot find resource with the requested URI: ${request.uri}")
                call.executionMachine.runBlockWithResult {
                    call.respond(response)
                }
            }
        }
    }

    companion object {
        private val DelayedStateAttribute = AttributeKey.newInstance<DelayedHandleState>("ktor-delayed-handle-state")
    }
}

