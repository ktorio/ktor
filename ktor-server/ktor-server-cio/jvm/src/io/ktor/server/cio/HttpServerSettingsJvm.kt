/*
 * Copyright 2014-2022 JetBrains s.r.o and contributors. Use of this source code is governed by the Apache 2.0 license.
 */

package io.ktor.server.cio

import io.ktor.network.tls.*
import io.ktor.server.engine.*
import java.io.*
import java.security.*

internal actual fun TLSConfigBuilder.takeFromConnector(connectorConfig: EngineSSLConnectorConfig) {
    authentication(connectorConfig)
    verification(connectorConfig)
}

private fun TLSConfigBuilder.authentication(connectorConfig: EngineSSLConnectorConfig) {
    connectorConfig.authentication?.let { config ->
        config.keyStoreProvider?.resolveKeyStore()?.let { keyStore ->
            authentication(config.privateKeyPassword) {
                keyStore(keyStore)
            }
            return
        }
    }

    //old configuration
    authentication(connectorConfig.privateKeyPassword) {
        keyStore(connectorConfig.resolveKeyStore())
    }
}

private fun TLSConfigBuilder.verification(connectorConfig: EngineSSLConnectorConfig) {
    val trustStore =
        connectorConfig.verification?.trustStoreProvider?.resolveKeyStore()
            ?: connectorConfig.resolveTrustStore() //old configuration
            ?: return
    verification {
        trustStore(trustStore)
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

//TODO: make it public and move to core
private fun EngineSSLConnectorConfig.resolveTrustStore(): KeyStore? = trustStore ?: trustStorePath?.let { file ->
    FileInputStream(file).use { fis ->
        KeyStore.getInstance("JKS").also { it.load(fis, null) }
    }
}
