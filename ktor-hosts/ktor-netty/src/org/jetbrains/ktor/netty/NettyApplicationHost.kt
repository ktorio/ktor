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
import org.jetbrains.ktor.nio.*
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


    private class DelayedHandleState(val request: HttpRequest, val channel: AsyncReadChannel, val urlEncodedParameters: () -> ValuesMap)

    inner class HostHttpHandler : SimpleChannelInboundHandler<HttpRequest>() {
        override fun channelRead0(context: ChannelHandlerContext, request: HttpRequest) {
            val requestContentType = request.headers().get(HttpHeaders.ContentType)?.let { ContentType.parse(it) }

            if (requestContentType != null && requestContentType.match(ContentType.Application.FormUrlEncoded)) {
                val urlEncodedHandler = FormUrlEncodedHandler(Charsets.UTF_8)
                context.pipeline().addLast(urlEncodedHandler)

                context.attr(DelayedStateAttribute).set(DelayedHandleState(request, AsyncEmptyChannel, { urlEncodedHandler.values }))
            } else {
                context.channel().config().isAutoRead = false
                val readHandler = BodyHandlerChannelAdapter(context)
                context.pipeline().addLast(readHandler)

                startHandleRequest(context, request, readHandler, { ValuesMap.Empty })
            }
        }

        override fun exceptionCaught(ctx: ChannelHandlerContext, cause: Throwable) {
            config.log.error("Application ${application.javaClass} cannot fulfill the request", cause);
            ctx.close()
        }

        override fun channelReadComplete(context: ChannelHandlerContext) {
            context.flush()

            val state = context.attr(DelayedStateAttribute)?.andRemove
            if (state != null) {
                startHandleRequest(context, state.request, state.channel, state.urlEncodedParameters)
            }
        }

        private fun startHandleRequest(context: ChannelHandlerContext, request: HttpRequest, channel: AsyncReadChannel, urlEncodedParameters: () -> ValuesMap) {
            val call = NettyApplicationCall(application, context, request, channel, urlEncodedParameters)
            val pipelineState = call.execute(application)
            if (pipelineState != PipelineState.Executing && !call.completed) {
                val response = HttpStatusContent(HttpStatusCode.NotFound, "Cannot find resource with the requested URI: ${request.uri}")
                call.executionMachine.executeInLoop {
                    call.respond(response)
                }
            }
        }

        private inline fun PipelineMachine.executeInLoop(block: () -> Unit): Unit {
            try {
                block()
            } catch (e: PipelineContinue) {
                stateLoopMachine()
            } catch (e: PipelineControlFlow) {
            }
        }

        private fun PipelineMachine.stateLoopMachine() {
            do {
                try {
                    proceed()
                } catch (e: PipelineContinue) {
                }
            } while (true);
        }

    }

    companion object {
        private val DelayedStateAttribute = AttributeKey.newInstance<DelayedHandleState>("ktor-delayed-handle-state")
    }
}

