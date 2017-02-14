package org.jetbrains.ktor.netty

import io.netty.channel.*
import io.netty.channel.socket.*
import io.netty.handler.codec.http.*
import io.netty.handler.codec.http2.*
import io.netty.handler.logging.*
import io.netty.handler.ssl.*
import io.netty.handler.stream.*
import io.netty.handler.timeout.*
import org.jetbrains.ktor.host.*
import javax.net.ssl.*

class NettyChannelInitializer(val host: NettyApplicationHost, val connector: HostConnectorConfig) : ChannelInitializer<SocketChannel>() {
    override fun initChannel(ch: SocketChannel) {
        val pipeline = ch.pipeline()
        if (connector is HostSSLConnectorConfig) {
            val kmf = KeyManagerFactory.getInstance(KeyManagerFactory.getDefaultAlgorithm())
            val password = connector.privateKeyPassword()
            kmf.init(connector.keyStore, password)
            password.fill('\u0000')

            val sslHandler = SslContextBuilder.forServer(kmf).apply {
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
            }.build().newHandler(ch.alloc())
            pipeline.addLast("ssl", sslHandler)
        }

        if (alpnProvider != null) {
            pipeline.addLast(NegotiatedPipelineInitializer())
        } else {
            configurePipeline(pipeline, ApplicationProtocolNames.HTTP_1_1)
        }
    }

    fun configurePipeline(pipeline: ChannelPipeline, protocol: String) {
        when (protocol) {
            ApplicationProtocolNames.HTTP_2 -> {
                val connection = DefaultHttp2Connection(true)
                val writer = DefaultHttp2FrameWriter()
                val reader = DefaultHttp2FrameReader(false)

                val encoder = DefaultHttp2ConnectionEncoder(connection, writer)
                val decoder = DefaultHttp2ConnectionDecoder(connection, encoder, reader)

/*
                pipeline.addLast(HostHttp2Handler(encoder, decoder, Http2Settings()))
                pipeline.addLast(Multiplexer(pipeline.channel(), HostHttpHandler(this@NettyApplicationHost, connection, byteBufferPool, hostPipeline)))
*/
            }
            ApplicationProtocolNames.HTTP_1_1 -> {
                with(pipeline) {
                    //addLast(LoggingHandler(LogLevel.INFO))
                    addLast(HttpServerCodec())
                    //addLast(LoggingHandler(LogLevel.INFO))
                    addLast(ChunkedWriteHandler())
               //     addLast(LoggingHandler(LogLevel.INFO))
                    addLast(WriteTimeoutHandler(10))
                    addLast(NettyHostHttp1Handler(host))
                }
            }
            else -> {
                host.application.environment.log.error("Unsupported protocol $protocol")
                pipeline.close()
            }
        }
    }

    inner class NegotiatedPipelineInitializer : ApplicationProtocolNegotiationHandler(ApplicationProtocolNames.HTTP_1_1) {
        override fun configurePipeline(ctx: ChannelHandlerContext, protocol: String) = configurePipeline(ctx.pipeline(), protocol)
    }

    companion object {
        val alpnProvider by lazy { findAlpnProvider() }

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