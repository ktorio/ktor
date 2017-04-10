package org.jetbrains.ktor.jetty

import kotlinx.coroutines.experimental.*
import org.eclipse.jetty.alpn.server.*
import org.eclipse.jetty.http.*
import org.eclipse.jetty.http2.*
import org.eclipse.jetty.http2.server.*
import org.eclipse.jetty.io.*
import org.eclipse.jetty.server.*
import org.eclipse.jetty.server.handler.*
import org.eclipse.jetty.util.ssl.*
import org.jetbrains.ktor.cio.*
import org.jetbrains.ktor.cio.ByteBufferPool
import org.jetbrains.ktor.host.*
import org.jetbrains.ktor.http.*
import java.nio.*
import java.util.concurrent.*
import javax.servlet.*
import javax.servlet.http.*

/**
 * [ApplicationHost] implementation for running standalone Jetty Host
 */
class JettyApplicationHost(environment: ApplicationHostEnvironment,
                           jettyServer: () -> Server = ::Server) : BaseApplicationHost(environment) {

    private val server = jettyServer().apply {
        connectors = environment.connectors.map { ktorConnector ->
            val httpConfig = HttpConfiguration().apply {
                sendServerVersion = false
                sendDateHeader = false

                if (ktorConnector.type == ConnectorType.HTTPS) {
                    addCustomizer(SecureRequestCustomizer())
                }
            }

            val alpnAvailable = try {
                NegotiatingServerConnectionFactory.checkProtocolNegotiationAvailable()
                true
            } catch (e: Throwable) {
                false
            }

            val connectionFactories = when (ktorConnector.type) {
                ConnectorType.HTTP -> arrayOf(HttpConnectionFactory(httpConfig), HTTP2CServerConnectionFactory(httpConfig))
                ConnectorType.HTTPS -> arrayOf(SslConnectionFactory(SslContextFactory().apply {
                    if (alpnAvailable) {
                        cipherComparator = HTTP2Cipher.COMPARATOR
                        isUseCipherSuitesOrder = true
                    }

                    keyStore = (ktorConnector as HostSSLConnectorConfig).keyStore
                    setKeyManagerPassword(String(ktorConnector.privateKeyPassword()))
                    setKeyStorePassword(String(ktorConnector.keyStorePassword()))

                    setExcludeCipherSuites("SSL_RSA_WITH_DES_CBC_SHA",
                            "SSL_DHE_RSA_WITH_DES_CBC_SHA", "SSL_DHE_DSS_WITH_DES_CBC_SHA",
                            "SSL_RSA_EXPORT_WITH_RC4_40_MD5",
                            "SSL_RSA_EXPORT_WITH_DES40_CBC_SHA",
                            "SSL_DHE_RSA_EXPORT_WITH_DES40_CBC_SHA",
                            "SSL_DHE_DSS_EXPORT_WITH_DES40_CBC_SHA")
                }, if (alpnAvailable) "alpn" else HttpVersion.HTTP_1_1.asString()),
                        if (alpnAvailable) {
                            ALPNServerConnectionFactory().apply {
                                defaultProtocol = HttpVersion.HTTP_1_1.asString()
                            }
                        } else null,
                        if (alpnAvailable) HTTP2ServerConnectionFactory(httpConfig) else HTTP2CServerConnectionFactory(httpConfig),
                        HttpConnectionFactory(httpConfig)).filterNotNull().toTypedArray()
                else -> throw IllegalArgumentException("Connector type ${ktorConnector.type} is not supported by Jetty host implementation")
            }

            ServerConnector(this, *connectionFactories).apply {
                host = ktorConnector.host
                port = ktorConnector.port
            }
        }.toTypedArray()

        handler = Handler()
    }

    class Ticket(bb: ByteBuffer) : ReleasablePoolTicket(bb)

    private val byteBufferPool = object : ByteBufferPool {
        val jbp = MappedByteBufferPool(16)

        override fun allocate(size: Int) = Ticket(jbp.acquire(size, false).apply { clear() })
        override fun release(buffer: PoolTicket) {
            jbp.release(buffer.buffer)
            (buffer as Ticket).release()
        }
    }


    private inner class Handler : AbstractHandler() {
        private val dispatcher by lazy { JettyCoroutinesDispatcher(server.threadPool) }
        private val MULTI_PART_CONFIG = MultipartConfigElement(System.getProperty("java.io.tmpdir"))

        override fun handle(target: String, baseRequest: Request, request: HttpServletRequest, response: HttpServletResponse) {
            val call = JettyApplicationCall(application, server, request, response, byteBufferPool, { call, block, next ->
                if (baseRequest.httpChannel.httpTransport.isPushSupported) {
                    baseRequest.pushBuilder.apply {
                        val builder = DefaultResponsePushBuilder(call)
                        builder.block()

                        this.method(builder.method.value)
                        this.path(builder.url.encodedPath)
                        this.queryString(builder.url.build().substringAfter('?', ""))

                        push()
                    }
                } else {
                    next()
                }
            })

            try {
                val contentType = request.contentType
                if (contentType != null && contentType.startsWith("multipart/")) {
                    baseRequest.setAttribute(Request.__MULTIPART_CONFIG_ELEMENT, MULTI_PART_CONFIG)
                    // TODO someone reported auto-cleanup issues so we have to check it
                }

                request.startAsync()
                request.asyncContext.timeout = 0 // Overwrite any default non-null timeout to prevent multiple dispatches
                baseRequest.isHandled = true

                launch(dispatcher) {
                    try {
                        pipeline.execute(call)
                    } finally {
                        request.asyncContext?.complete()
                    }
                }
            } catch(ex: Throwable) {
                environment.log.error("Application ${application::class.java} cannot fulfill the request", ex)

                launch(dispatcher) {
                    call.respond(HttpStatusCode.InternalServerError)
                }
            }
        }
    }

    override fun start(wait: Boolean) : JettyApplicationHost {
        environment.start()
        server.start()
        if (wait) {
            server.join()
            stop(1, 5, TimeUnit.SECONDS)
        }
        return this
    }

    override fun stop(gracePeriod: Long, timeout: Long, timeUnit: TimeUnit) {
        server.stopTimeout = timeUnit.toMillis(timeout)
        server.stop()
        environment.stop()
    }

    override fun toString(): String {
        return "Jetty($environment)"
    }
}
