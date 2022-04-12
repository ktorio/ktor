/*
* Copyright 2014-2021 JetBrains s.r.o and contributors. Use of this source code is governed by the Apache 2.0 license.
*/

package io.ktor.server.jetty

import io.ktor.server.engine.*
import org.eclipse.jetty.alpn.server.*
import org.eclipse.jetty.http.*
import org.eclipse.jetty.http2.*
import org.eclipse.jetty.http2.server.*
import org.eclipse.jetty.server.*
import org.eclipse.jetty.util.ssl.*

internal fun Server.initializeServer(environment: ApplicationEngineEnvironment) {
    environment.connectors.map { connector ->
        val httpConfig = HttpConfiguration().apply {
            sendServerVersion = false
            sendDateHeader = false

            if (connector.type == ConnectorType.HTTPS) {
                addCustomizer(SecureRequestCustomizer())
            }
        }

        var alpnAvailable = false
        var alpnConnectionFactory: ALPNServerConnectionFactory?
        var http2ConnectionFactory: HTTP2ServerConnectionFactory?

        try {
            alpnConnectionFactory = ALPNServerConnectionFactory().apply {
                defaultProtocol = HttpVersion.HTTP_1_1.asString()
            }
            http2ConnectionFactory = HTTP2ServerConnectionFactory(httpConfig)
            alpnAvailable = true
        } catch (t: Throwable) {
            // ALPN or HTTP/2 implemented is not available
            alpnConnectionFactory = null
            http2ConnectionFactory = null
        }

        val connectionFactories = when (connector) {
            is EngineSSLConnectorConfig -> arrayOf(
                SslConnectionFactory(
                    SslContextFactory.Server().apply {
                        if (alpnAvailable) {
                            cipherComparator = HTTP2Cipher.COMPARATOR
                            isUseCipherSuitesOrder = true
                        }

                        configureAuthentication(connector)
                        configureVerification(connector)

                        addExcludeCipherSuites(
                            "SSL_RSA_WITH_DES_CBC_SHA",
                            "SSL_DHE_RSA_WITH_DES_CBC_SHA",
                            "SSL_DHE_DSS_WITH_DES_CBC_SHA",
                            "SSL_RSA_EXPORT_WITH_RC4_40_MD5",
                            "SSL_RSA_EXPORT_WITH_DES40_CBC_SHA",
                            "SSL_DHE_RSA_EXPORT_WITH_DES40_CBC_SHA",
                            "SSL_DHE_DSS_EXPORT_WITH_DES40_CBC_SHA"
                        )
                    },
                    if (alpnAvailable) "alpn" else HttpVersion.HTTP_1_1.asString()
                ),
                alpnConnectionFactory,
                http2ConnectionFactory ?: HTTP2CServerConnectionFactory(httpConfig),
                HttpConnectionFactory(httpConfig)
            ).filterNotNull().toTypedArray()
            else -> arrayOf(HttpConnectionFactory(httpConfig), HTTP2CServerConnectionFactory(httpConfig))
        }

        ServerConnector(this, *connectionFactories).apply {
            host = connector.host
            port = connector.port
        }
    }.forEach { this.addConnector(it) }
}

private fun SslContextFactory.Server.configureAuthentication(connectorConfig: EngineSSLConnectorConfig) {
    connectorConfig.authentication?.let { config ->
        config.keyStoreProvider?.let {
            keyStore = it.resolveKeyStore()
            setKeyManagerPassword(String(config.privateKeyPassword()))
            return
        }
    }

    //old configuration
    keyStore = connectorConfig.keyStore
    setKeyStorePassword(String(connectorConfig.keyStorePassword()))
    setKeyManagerPassword(String(connectorConfig.privateKeyPassword()))
}

private fun SslContextFactory.Server.configureVerification(connectorConfig: EngineSSLConnectorConfig) {
    connectorConfig.verification?.let { config ->
        config.trustStoreProvider?.let {
            trustStore = it.resolveKeyStore()
            needClientAuth = true
            return
        }
    }

    //old configuration
    needClientAuth = when {
        connectorConfig.trustStore != null -> {
            trustStore = connectorConfig.trustStore
            true
        }
        connectorConfig.trustStorePath != null -> {
            trustStorePath = connectorConfig.trustStorePath?.absolutePath
            true
        }
        else -> false
    }
}
