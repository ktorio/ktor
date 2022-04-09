/*
 * Copyright 2014-2022 JetBrains s.r.o and contributors. Use of this source code is governed by the Apache 2.0 license.
 */

package io.ktor.server.cio

import io.ktor.network.tls.*
import io.ktor.server.engine.*
import java.io.*
import java.security.*

internal actual fun HttpServerSettings(
    connectionIdleTimeoutSeconds: Long,
    connectorConfig: EngineConnectorConfig
): HttpServerSettings = HttpServerSettings(
    host = connectorConfig.host,
    port = connectorConfig.port,
    connectionIdleTimeoutSeconds = connectionIdleTimeoutSeconds,
    tlsConfig = when (connectorConfig) {
        is EngineSSLConnectorConfig -> TLSConfig(isClient = false) {
//            serverName = connectorConfig.host //TODO?
            authentication(connectorConfig.privateKeyPassword) {
                keyStore(connectorConfig.resolveKeyStore())
            }
            connectorConfig.resolveTrustStore()?.let {
                verification {
                    trustStore(it)
                }
            }
        }
        else -> null
    }
)

//TODO: make it public and move to core
private fun EngineSSLConnectorConfig.resolveKeyStore(): KeyStore = keyStorePath?.let { file ->
    FileInputStream(file).use { fis ->
        val password = keyStorePassword()
        val instance = KeyStore.getInstance("JKS").also { it.load(fis, password) }
        password.fill('\u0000')
        instance
    }
} ?: keyStore

//TODO: make it public and move to core
private fun EngineSSLConnectorConfig.resolveTrustStore(): KeyStore? = trustStore ?: trustStorePath?.let { file ->
    FileInputStream(file).use { fis ->
        KeyStore.getInstance("JKS").also { it.load(fis, null) }
    }
}
