/*
 * Copyright 2014-2019 JetBrains s.r.o and contributors. Use of this source code is governed by the Apache 2.0 license.
 */

package io.ktor.server.engine

import java.io.*
import java.security.*

/**
 * Represents an SSL connector configuration.
 */
public interface EngineSSLConnectorConfig : EngineConnectorConfig {
    /**
     * KeyStore where a certificate is stored
     */
    public val keyStore: KeyStore

    /**
     * File where the keystore is located
     */
    public val keyStorePath: File?

    /**
     * TLS key alias
     */
    public val keyAlias: String

    /**
     * Keystore password provider
     */
    public val keyStorePassword: () -> CharArray

    /**
     * Private key password provider
     */
    public val privateKeyPassword: () -> CharArray
}

/**
 * Adds a secure connector to this engine environment
 */
public inline fun ApplicationEngineEnvironmentBuilder.sslConnector(
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
 */
public class EngineSSLConnectorBuilder(
    override var keyStore: KeyStore,
    override var keyAlias: String,
    override var keyStorePassword: () -> CharArray,
    override val privateKeyPassword: () -> CharArray
) : EngineConnectorBuilder(ConnectorType.HTTPS), EngineSSLConnectorConfig {
    override var keyStorePath: File? = null
}
