/*
 * Copyright 2014-2019 JetBrains s.r.o and contributors. Use of this source code is governed by the Apache 2.0 license.
 */

package io.ktor.server.engine

import java.io.*
import java.security.*

/**
 * Represents a type of a connector, e.g HTTP or HTTPS.
 * @param name name of the connector.
 *
 * Some engines can support other connector types, hence not a enum.
 */
public data class ConnectorType(val name: String) {
    public companion object {
        /**
         * Non-secure HTTP connector.
         * 1
         */
        public val HTTP: ConnectorType = ConnectorType("HTTP")

        /**
         * Secure HTTP connector.
         */
        public val HTTPS: ConnectorType = ConnectorType("HTTPS")
    }
}

/**
 * Represents a connector configuration.
 */
public interface EngineConnectorConfig {
    /**
     * Type of the connector, e.g HTTP or HTTPS.
     */
    public val type: ConnectorType

    /**
     * The network interface this host binds to as an IP address or a hostname.  If null or 0.0.0.0, then bind to all interfaces.
     */
    public val host: String

    /**
     * The port this application should be bound to.
     */
    public val port: Int
}

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

    /**
     * Store of trusted certificates for verifying the remote endpoint's certificate.
     *
     * The engine tries to use [trustStore] first and uses [trustStorePath] as a fallback.
     *
     * If [trustStore] and [trustStorePath] are both null, the endpoint's certificate will not be verified.
     */
    public val trustStore: KeyStore?

    /**
     * File with trusted certificates (JKS) for verifying the remote endpoint's certificate.
     *
     * The engine tries to use [trustStore] first and uses [trustStorePath] as a fallback.
     *
     * If [trustStore] and [trustStorePath] are both null, the endpoint's certificate will not be verified.
     */
    public val trustStorePath: File?
}

/**
 * Adds a non-secure connector to this engine environment
 */
public inline fun ApplicationEngineEnvironmentBuilder.connector(builder: EngineConnectorBuilder.() -> Unit) {
    connectors.add(EngineConnectorBuilder().apply(builder))
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
 * Mutable implementation of EngineConnectorConfig for building connectors programmatically
 */
public open class EngineConnectorBuilder(
    override val type: ConnectorType = ConnectorType.HTTP
) : EngineConnectorConfig {
    override var host: String = "0.0.0.0"
    override var port: Int = 80

    override fun toString(): String {
        return "${type.name} $host:$port"
    }
}

/**
 * Mutable implementation of EngineSSLConnectorConfig for building connectors programmatically
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
}
