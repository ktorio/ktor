/*
 * Copyright 2014-2022 JetBrains s.r.o and contributors. Use of this source code is governed by the Apache 2.0 license.
 */

package io.ktor.server.cio

import io.ktor.network.tls.*
import io.ktor.server.engine.*
import io.ktor.util.*
import kotlinx.coroutines.*
import java.io.*
import java.security.*
import javax.net.ssl.*

@OptIn(InternalAPI::class)
internal actual fun HttpServerSettings(
    connectionIdleTimeoutSeconds: Long,
    connectorConfig: EngineConnectorConfig
): HttpServerSettings {
    if (connectorConfig is EngineSSLConnectorConfig) {
        return HttpServerSettings(
            host = connectorConfig.host,
            port = connectorConfig.port,
            connectionIdleTimeoutSeconds = connectionIdleTimeoutSeconds
        ) { socket ->
            val context = SSLContext.getInstance("TLS")

            context.init(
                connectorConfig.keyManagerFactory().keyManagers,
                connectorConfig.trustManagerFactory()?.trustManagers,
                null
            )

            val engine = context.createSSLEngine()
            engine.useClientMode = false
            if (connectorConfig.hasTrustStore()) {
                engine.needClientAuth = true
            }

            socket.ssl(Dispatchers.IO, engine)
        }
    }
    return HttpServerSettings(
        host = connectorConfig.host,
        port = connectorConfig.port,
        connectionIdleTimeoutSeconds = connectionIdleTimeoutSeconds
    )
}

private fun EngineSSLConnectorConfig.hasTrustStore() = trustStore != null || trustStorePath != null

private fun EngineSSLConnectorConfig.trustManagerFactory(): TrustManagerFactory? {
    val trustStore = trustStore ?: trustStorePath?.let { file ->
        FileInputStream(file).use { fis ->
            KeyStore.getInstance("JKS").also { it.load(fis, null) }
        }
    }
    return trustStore?.let { store ->
        TrustManagerFactory.getInstance(TrustManagerFactory.getDefaultAlgorithm()).also { it.init(store) }
    }
}

private fun EngineSSLConnectorConfig.keyManagerFactory(): KeyManagerFactory {
    val keyStore = keyStorePath?.let { file ->
        FileInputStream(file).use { fis ->
            val password = keyStorePassword()
            val instance = KeyStore.getInstance("JKS").also { it.load(fis, password) }
            password.fill('\u0000')
            instance
        }
    } ?: keyStore
    val kmf = KeyManagerFactory.getInstance(KeyManagerFactory.getDefaultAlgorithm())
    val password = privateKeyPassword()
    kmf.init(keyStore, password)
    password.fill('\u0000')
    return kmf
}
