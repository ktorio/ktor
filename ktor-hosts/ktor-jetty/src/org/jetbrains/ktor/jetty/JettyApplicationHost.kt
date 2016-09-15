package org.jetbrains.ktor.jetty

import org.eclipse.jetty.alpn.server.*
import org.eclipse.jetty.http.*
import org.eclipse.jetty.http2.*
import org.eclipse.jetty.http2.server.*
import org.eclipse.jetty.io.*
import org.eclipse.jetty.server.*
import org.eclipse.jetty.server.handler.*
import org.eclipse.jetty.util.ssl.*
import org.jetbrains.ktor.application.*
import org.jetbrains.ktor.host.*
import org.jetbrains.ktor.http.*
import org.jetbrains.ktor.nio.*
import org.jetbrains.ktor.nio.ByteBufferPool
import org.jetbrains.ktor.pipeline.*
import org.jetbrains.ktor.transform.*
import java.nio.*
import javax.servlet.*
import javax.servlet.http.*

/**
 * [ApplicationHost] implementation for running standalone Jetty Host
 */
class JettyApplicationHost(override val hostConfig: ApplicationHostConfig,
                           val environment: ApplicationEnvironment,
                           val applicationLifecycle: ApplicationLifecycle,
                           jettyServer: () -> Server = ::Server) : ApplicationHost, ApplicationHostStartable {

    private val application: Application get() = applicationLifecycle.application

    constructor(hostConfig: ApplicationHostConfig, environment: ApplicationEnvironment)
    : this(hostConfig, environment, ApplicationLoader(environment, hostConfig.autoreload))

    private val server = jettyServer().apply {
        connectors = hostConfig.connectors.map { ktorConnector ->
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

    init {
        applicationLifecycle.onBeforeInitializeApplication {
            install(TransformationSupport).registerDefaultHandlers()
        }
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

    private val MULTI_PART_CONFIG = MultipartConfigElement(System.getProperty("java.io.tmpdir"))
    private val hostPipeline = defaultHostPipeline()

    private inner class Handler : AbstractHandler() {
        override fun handle(target: String, baseRequest: Request, request: HttpServletRequest, response: HttpServletResponse) {
            response.characterEncoding = "UTF-8"

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
                if (contentType != null && ContentType.parse(contentType).match(ContentType.MultiPart.Any)) {
                    baseRequest.setAttribute(Request.__MULTIPART_CONFIG_ELEMENT, MULTI_PART_CONFIG)
                    // TODO someone reported auto-cleanup issues so we have to check it
                }

                request.startAsync()
                baseRequest.isHandled = true

                call.executeOn(application.executor, hostPipeline).whenComplete { state, t ->
                    when (state) {
                        PipelineState.Finished, PipelineState.FinishedAll -> {
                            request.asyncContext.complete()
                        }
                        PipelineState.Failed -> {
                            environment.log.error("Application ${application.javaClass} cannot fulfill the request", t)
                            call.execution.runBlockWithResult {
                                call.respond(HttpStatusCode.InternalServerError)
                            }
                        }
                        null, PipelineState.Executing -> {}
                    }
                }
            } catch(ex: Throwable) {
                environment.log.error("Application ${application.javaClass} cannot fulfill the request", ex)
                call.execution.runBlockWithResult {
                    call.respond(HttpStatusCode.InternalServerError)
                }
            }
        }
    }

    override fun start(wait: Boolean) {
        applicationLifecycle.ensureApplication()
        environment.log.trace("Starting server...")

        server.start()
        environment.log.trace("Server running.")
        if (wait) {
            server.join()
            applicationLifecycle.dispose()
            environment.log.trace("Server stopped.")
        }
    }

    override fun stop() {
        server.stop()
        applicationLifecycle.dispose()
        environment.log.trace("Server stopped.")
    }
}
