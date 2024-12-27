/*
* Copyright 2014-2024 JetBrains s.r.o and contributors. Use of this source code is governed by the Apache 2.0 license.
*/

package io.ktor.server.jetty.jakarta

import io.ktor.server.engine.*
import org.eclipse.jetty.alpn.server.ALPNServerConnectionFactory
import org.eclipse.jetty.http3.server.HTTP3ServerConnectionFactory
import org.eclipse.jetty.quic.server.QuicServerConnector
import org.eclipse.jetty.quic.server.ServerQuicConfiguration
import org.eclipse.jetty.server.*
import org.eclipse.jetty.util.ssl.SslContextFactory
import java.nio.file.Paths


internal fun Server.initializeServer(configuration: JettyApplicationEngineBase.Configuration) {

    configuration.connectors.forEach { ktorConnector ->

        val httpConfig = HttpConfiguration().apply {
            sendServerVersion = false
            sendDateHeader = false
            idleTimeout = -1
        }

        when (ktorConnector.type) {

            ConnectorType.HTTP -> {

                ServerConnector(this, HttpConnectionFactory(httpConfig)).apply {
                    port = ktorConnector.port
                    host = ktorConnector.host
                    idleTimeout = configuration.idleTimeout
                    server.addConnector(this)
                }
            }

            ConnectorType.HTTPS -> {

                httpConfig.addCustomizer(SecureRequestCustomizer())
                val sslContextFactory = SslContextFactory.Server().apply {

                    keyStore = (ktorConnector as EngineSSLConnectorConfig).keyStore
                    keyManagerPassword = String(ktorConnector.privateKeyPassword())
                    keyStorePassword = String(ktorConnector.keyStorePassword())

                    needClientAuth = when {
                        ktorConnector.trustStore != null -> {
                            trustStore = ktorConnector.trustStore
                            true
                        }

                        ktorConnector.trustStorePath != null -> {
                            trustStorePath = ktorConnector.trustStorePath!!.absolutePath
                            true
                        }

                        else -> false
                    }

                    ktorConnector.enabledProtocols?.let {
                        setIncludeProtocols(*it.toTypedArray())
                    }

                    addExcludeCipherSuites(
                        "SSL_RSA_WITH_DES_CBC_SHA",
                        "SSL_DHE_RSA_WITH_DES_CBC_SHA",
                        "SSL_DHE_DSS_WITH_DES_CBC_SHA",
                        "SSL_RSA_EXPORT_WITH_RC4_40_MD5",
                        "SSL_RSA_EXPORT_WITH_DES40_CBC_SHA",
                        "SSL_DHE_RSA_EXPORT_WITH_DES40_CBC_SHA",
                        "SSL_DHE_DSS_EXPORT_WITH_DES40_CBC_SHA"
                    )
                }

                val http = HttpConnectionFactory(httpConfig)
                val ssl = SslConnectionFactory(sslContextFactory, http.protocol)
                val alpn = ALPNServerConnectionFactory(http.protocol.toString()).apply {
                    defaultProtocol = http.protocol.toString()
                }

                ServerConnector(server, 0, 0, ssl, alpn, http).apply {
                    port = ktorConnector.port
                    host = ktorConnector.host
                    idleTimeout = configuration.idleTimeout
                    server.addConnector(this)
                }

                val quicConfig = ServerQuicConfiguration(sslContextFactory, Paths.get(System.getProperty("java.io.tmpdir")))
                val http3 = HTTP3ServerConnectionFactory(quicConfig, httpConfig)

                QuicServerConnector(server, quicConfig, http3).apply {
                    port = ktorConnector.port
                    host = ktorConnector.host
                    idleTimeout = configuration.idleTimeout
                    server.addConnector(this)
                }
            }

            else -> throw IllegalArgumentException(
                "Connector type ${ktorConnector.type} is not supported by Jetty engine implementation"
            )
        }
    }
}
