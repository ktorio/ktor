package org.jetbrains.ktor.netty

import io.netty.channel.*
import io.netty.channel.socket.*
import io.netty.handler.codec.http.*
import io.netty.handler.codec.http2.*
import io.netty.handler.ssl.*
import io.netty.handler.timeout.*
import org.jetbrains.ktor.application.*
import org.jetbrains.ktor.host.*
import org.jetbrains.ktor.netty.http2.*
import java.security.*
import java.security.cert.*

class NettyChannelInitializer(val host: NettyApplicationHost, val connector: HostConnectorConfig) : ChannelInitializer<SocketChannel>() {
    private var sslContext: SslContext? = null

    init {
        if (connector is HostSSLConnectorConfig) {

            // It is better but netty-openssl doesn't support it
//              val kmf = KeyManagerFactory.getInstance(KeyManagerFactory.getDefaultAlgorithm())
//              kmf.init(ktorConnector.keyStore, password)
//              password.fill('\u0000')

            @Suppress("UNCHECKED_CAST")
            val chain1 = connector.keyStore.getCertificateChain(connector.keyAlias).toList() as List<X509Certificate>
            val certs = chain1.toList().toTypedArray<X509Certificate>()
            val password = connector.privateKeyPassword()
            val pk = connector.keyStore.getKey(connector.keyAlias, password) as PrivateKey
            password.fill('\u0000')

            sslContext = SslContextBuilder.forServer(pk, *certs)
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
        }
    }

    override fun initChannel(ch: SocketChannel) {
        with(ch.pipeline()) {
            if (connector is HostSSLConnectorConfig) {
                addLast("ssl", sslContext!!.newHandler(ch.alloc()))

                if (alpnProvider != null) {
                    addLast(NegotiatedPipelineInitializer())
                } else {
                    configurePipeline(this, ApplicationProtocolNames.HTTP_1_1)
                }
            } else {
                configurePipeline(this, ApplicationProtocolNames.HTTP_1_1)
            }
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

                pipeline.addLast(HostHttp2Handler(encoder, decoder, Http2Settings()))
                pipeline.addLast(Multiplexer(pipeline.channel(), NettyHostHttp2Handler(host, connection, host.pipeline)))
            }
            ApplicationProtocolNames.HTTP_1_1 -> {
                val handler = NettyHostHttp1Handler(host)

                with(pipeline) {
                    addLast("codec", HttpServerCodec())
                    addLast("timeout", WriteTimeoutHandler(10))
                    addLast("http1", handler)
                }

                pipeline.context("codec").fireChannelActive()
            }
            else -> {
                host.application.log.error("Unsupported protocol $protocol")
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
            try {
                Class.forName("sun.security.ssl.ALPNExtension", true, null)
                return SslProvider.JDK
            } catch (ignore: Throwable) {
            }

            try {
                if (OpenSsl.isAlpnSupported()) {
                    return SslProvider.OPENSSL
                }
            } catch (ignore: Throwable) {
            }

            return null
        }
    }
}