package org.jetbrains.ktor.netty

import io.netty.bootstrap.*
import io.netty.buffer.*
import io.netty.channel.*
import io.netty.channel.nio.*
import io.netty.channel.socket.*
import io.netty.channel.socket.nio.*
import io.netty.handler.codec.http.*
import io.netty.handler.codec.http2.*
import io.netty.handler.ssl.*
import io.netty.handler.stream.*
import io.netty.handler.timeout.*
import org.jetbrains.ktor.application.*
import org.jetbrains.ktor.features.*
import org.jetbrains.ktor.host.*
import org.jetbrains.ktor.netty.http2.*
import org.jetbrains.ktor.nio.*
import org.jetbrains.ktor.transform.*
import javax.net.ssl.*

/**
 * [ApplicationHost] implementation for running standalone Netty Host
 */
class NettyApplicationHost(override val hostConfig: ApplicationHostConfig,
                           val environment: ApplicationEnvironment,
                           val applicationLifecycle: ApplicationLifecycle) : ApplicationHost, ApplicationHostStartable {

    val application: Application get() = applicationLifecycle.application

    constructor(hostConfig: ApplicationHostConfig, environment: ApplicationEnvironment)
    : this(hostConfig, environment, ApplicationLoader(environment, hostConfig.autoreload))

    private val mainEventGroup = NioEventLoopGroup()
    private val workerEventGroup = NioEventLoopGroup()

    private val bootstraps = hostConfig.connectors.map { ktorConnector ->
        ServerBootstrap().apply {
            group(mainEventGroup, workerEventGroup)
            channel(NioServerSocketChannel::class.java)
            childHandler(object : ChannelInitializer<SocketChannel>() {
                override fun initChannel(ch: SocketChannel) {
                    val alpnProvider = findAlpnProvider()

                    with(ch.pipeline()) {
                        if (ktorConnector is HostSSLConnectorConfig) {
                            val kmf = KeyManagerFactory.getInstance(KeyManagerFactory.getDefaultAlgorithm())
                            val password = ktorConnector.privateKeyPassword()
                            kmf.init(ktorConnector.keyStore, password)
                            password.fill('\u0000')

                            addLast("ssl", SslContextBuilder.forServer(kmf)
                                    .apply {
                                        if (alpnProvider != null) {
                                            sslProvider(alpnProvider)
                                            ciphers(Http2SecurityUtil.CIPHERS, SupportedCipherSuiteFilter.INSTANCE)
                                            applicationProtocolConfig(ApplicationProtocolConfig(
                                                    ApplicationProtocolConfig.Protocol.ALPN,
                                                    ApplicationProtocolConfig.SelectorFailureBehavior.NO_ADVERTISE,
                                                    ApplicationProtocolConfig.SelectedListenerFailureBehavior.ACCEPT,
                                                    ApplicationProtocolNames.HTTP_2,
                                                    ApplicationProtocolNames.HTTP_1_1
                                            ))
                                        }
                                    }
                                    .build()
                                    .newHandler(ch.alloc()))
                        }

                        if (alpnProvider != null) {
                            addLast(Initializer())
                        } else {
                            configurePipeline(this, ApplicationProtocolNames.HTTP_1_1)
                        }
                    }
                }
            })
        }
    }

    private val hostPipeline = defaultHostPipeline()

    init {
        applicationLifecycle.onBeforeInitializeApplication {
            install(TransformationSupport).registerDefaultHandlers()
        }
    }

    class Ticket(val bb: ByteBuf) : ReleasablePoolTicket(bb.nioBuffer(0, bb.capacity()))
    private val byteBufferPool = object : ByteBufferPool {
        val nbp = PooledByteBufAllocator(false)

        override fun allocate(size: Int): PoolTicket {
            return Ticket(nbp.heapBuffer(size))
        }

        override fun release(buffer: PoolTicket) {
            (buffer as Ticket).let {
                it.bb.release()
                it.release()
            }
        }
    }

    override fun start(wait: Boolean) {
        applicationLifecycle.ensureApplication()
        environment.log.info("Starting server...")
        val channelFutures = bootstraps.zip(hostConfig.connectors).map { it.first.bind(it.second.host, it.second.port) }
        environment.log.info("Server running.")

        if (wait) {
            channelFutures.map { it.channel().closeFuture() }.forEach { it.sync() }
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

    fun configurePipeline(pipeline: ChannelPipeline, protocol: String) {
        when (protocol) {
            ApplicationProtocolNames.HTTP_2 -> {
                val connection = DefaultHttp2Connection(true)
                val writer = DefaultHttp2FrameWriter()
                val reader = DefaultHttp2FrameReader(false)

                val encoder = DefaultHttp2ConnectionEncoder(connection, writer)
                val decoder = DefaultHttp2ConnectionDecoder(connection, encoder, reader)

                pipeline.addLast(HostHttp2Handler(encoder, decoder, Http2Settings()))
                pipeline.addLast(Multiplexer(pipeline.channel(), HostHttpHandler(this@NettyApplicationHost, connection, byteBufferPool, hostPipeline)))
            }
            ApplicationProtocolNames.HTTP_1_1 -> {
                with(pipeline) {
                    addLast(HttpServerCodec())
                    addLast(ChunkedWriteHandler())
                    addLast(WriteTimeoutHandler(10))
                    addLast(HostHttpHandler(this@NettyApplicationHost, null, byteBufferPool, hostPipeline))
                }
            }
            else -> {
                application.environment.log.error("Unsupported protocol $protocol")
                pipeline.close()
            }
        }
    }

    inner class Initializer : ApplicationProtocolNegotiationHandler(ApplicationProtocolNames.HTTP_1_1) {
        override fun configurePipeline(ctx: ChannelHandlerContext, protocol: String) = this@NettyApplicationHost.configurePipeline(ctx.pipeline(), protocol)
    }

    companion object {
        fun findAlpnProvider(): SslProvider? {
            val jettyAlpn = try {
                Class.forName("sun.security.ssl.ALPNExtension", true, null)
                true
            } catch (t: Throwable) {
                false
            }

            return when {
                jettyAlpn -> SslProvider.JDK
                else -> null
            }
        }
    }

}

