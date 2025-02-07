/*
 * Copyright 2014-2021 JetBrains s.r.o and contributors. Use of this source code is governed by the Apache 2.0 license.
 */

package io.ktor.server.engine

import java.io.*
import java.security.*

/**
 * Adds a secure connector to this engine environment
 *
 * [Report a problem](https://ktor.io/feedback/?fqname=io.ktor.server.engine.sslConnector)
 */
public inline fun ApplicationEngine.Configuration.sslConnector(
    keyStore: KeyStore,
    keyAlias: String,
    noinline keyStorePassword: () -> CharArray,
    noinline privateKeyPassword: () -> CharArray,
    builder: EngineSSLConnectorBuilder.() -> Unit
) {
    connectors.add(EngineSSLConnectorBuilder(keyStore, keyAlias, keyStorePassword, privateKeyPassword).apply(builder))
}

/**
 * Mutable implementation of EngineSSLConnectorConfig for building connectors programmatically
 *
 * [Report a problem](https://ktor.io/feedback/?fqname=io.ktor.server.engine.EngineSSLConnectorBuilder)
 */
public class EngineSSLConnectorBuilder(
    override var keyStore: KeyStore,
    override var keyAlias: String,
    override var keyStorePassword: () -> CharArray,
    override var privateKeyPassword: () -> CharArray
) : EngineConnectorBuilder(ConnectorType.HTTPS), EngineSSLConnectorConfig {
    override var keyStorePath: File? = null
    override var trustStore: KeyStore? = null
    override var trustStorePath: File? = null
    override var port: Int = 443
    override var enabledProtocols: List<String>? = null
}

/**
 * Represents an SSL connector configuration.
 *
 * [Report a problem](https://ktor.io/feedback/?fqname=io.ktor.server.engine.EngineSSLConnectorConfig)
 */
public interface EngineSSLConnectorConfig : EngineConnectorConfig {
    /**
     * KeyStore where a certificate is stored
     *
     * [Report a problem](https://ktor.io/feedback/?fqname=io.ktor.server.engine.EngineSSLConnectorConfig.keyStore)
     */
    public val keyStore: KeyStore

    /**
     * File where the keystore is located
     *
     * [Report a problem](https://ktor.io/feedback/?fqname=io.ktor.server.engine.EngineSSLConnectorConfig.keyStorePath)
     */
    public val keyStorePath: File?

    /**
     * TLS key alias
     *
     * [Report a problem](https://ktor.io/feedback/?fqname=io.ktor.server.engine.EngineSSLConnectorConfig.keyAlias)
     */
    public val keyAlias: String

    /**
     * Keystore password provider
     *
     * [Report a problem](https://ktor.io/feedback/?fqname=io.ktor.server.engine.EngineSSLConnectorConfig.keyStorePassword)
     */
    public val keyStorePassword: () -> CharArray

    /**
     * Private key password provider
     *
     * [Report a problem](https://ktor.io/feedback/?fqname=io.ktor.server.engine.EngineSSLConnectorConfig.privateKeyPassword)
     */
    public val privateKeyPassword: () -> CharArray

    /**
     * Store of trusted certificates for verifying the remote endpoint's certificate.
     *
     * The engine tries to use [trustStore] first and uses [trustStorePath] as a fallback.
     *
     * If [trustStore] and [trustStorePath] are both null, the endpoint's certificate will not be verified.
     *
     * [Report a problem](https://ktor.io/feedback/?fqname=io.ktor.server.engine.EngineSSLConnectorConfig.trustStore)
     */
    public val trustStore: KeyStore?

    /**
     * File with trusted certificates (JKS) for verifying the remote endpoint's certificate.
     *
     * The engine tries to use [trustStore] first and uses [trustStorePath] as a fallback.
     *
     * If [trustStore] and [trustStorePath] are both null, the endpoint's certificate will not be verified.
     *
     * [Report a problem](https://ktor.io/feedback/?fqname=io.ktor.server.engine.EngineSSLConnectorConfig.trustStorePath)
     */
    public val trustStorePath: File?

    /**
     *  Enabled protocol versions
     *
     * [Report a problem](https://ktor.io/feedback/?fqname=io.ktor.server.engine.EngineSSLConnectorConfig.enabledProtocols)
     */
    public val enabledProtocols: List<String>?
}

/**
 * Returns new instance of [EngineConnectorConfig] based on [this] with modified port
 *
 * [Report a problem](https://ktor.io/feedback/?fqname=io.ktor.server.engine.withPort)
 */
public actual fun EngineConnectorConfig.withPort(otherPort: Int): EngineConnectorConfig = when (this) {
    is EngineSSLConnectorBuilder -> object : EngineSSLConnectorConfig by this {
        override val port: Int = otherPort
    }
    else -> object : EngineConnectorConfig by this {
        override val port: Int = otherPort
    }
}
