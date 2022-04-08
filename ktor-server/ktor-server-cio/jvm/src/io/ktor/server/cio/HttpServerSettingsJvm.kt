/*
 * Copyright 2014-2022 JetBrains s.r.o and contributors. Use of this source code is governed by the Apache 2.0 license.
 */

package io.ktor.server.cio

import io.ktor.network.sockets.*
import io.ktor.network.tls.*
import io.ktor.server.engine.*
import java.io.*
import java.security.*
import javax.net.ssl.*
import kotlin.coroutines.*

internal actual fun HttpServerSettings(
    connectionIdleTimeoutSeconds: Long,
    connectorConfig: EngineConnectorConfig
): HttpServerSettings {
    val interceptor: suspend (Socket, CoroutineContext) -> Socket = when (connectorConfig) {
        is EngineSSLConnectorConfig -> {
            val config = TLSConfig(isClient = false) {
                trustManager = connectorConfig.trustManagerFactory()?.trustManagers?.firstOrNull()
                authentication(connectorConfig.privateKeyPassword) {
                    keyStore(connectorConfig.resolveKeyStore())
                }
            };
            { socket, context -> socket.tls(context, config) }
        }
        else -> { socket, _ -> socket }
    }

    return HttpServerSettings(
        host = connectorConfig.host,
        port = connectorConfig.port,
        connectionIdleTimeoutSeconds = connectionIdleTimeoutSeconds,
        interceptor = interceptor
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

//TODO: make it public and move to core
private fun EngineSSLConnectorConfig.resolveKeyStore(): KeyStore = keyStorePath?.let { file ->
    FileInputStream(file).use { fis ->
        val password = keyStorePassword()
        val instance = KeyStore.getInstance("JKS").also { it.load(fis, password) }
        password.fill('\u0000')
        instance
    }
} ?: keyStore
