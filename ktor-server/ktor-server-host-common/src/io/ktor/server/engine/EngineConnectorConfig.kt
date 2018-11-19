package io.ktor.server.engine

import java.io.*
import java.security.*

/**
 * Represents a type of a connector, e.g HTTP or HTTPS.
 * @param name name of the connector.
 *
 * Some engines can support other connector types, hence not a enum.
 */
data class ConnectorType(val name: String) {
    companion object {
        /**
         * Non-secure HTTP connector.
         * 1
         */
        val HTTP = ConnectorType("HTTP")

        /**
         * Secure HTTP connector.
         */
        val HTTPS = ConnectorType("HTTPS")
    }
}

/**
 * Represents a connector configuration.
 */
interface EngineConnectorConfig {
    /**
     * Type of the connector, e.g HTTP or HTTPS.
     */
    val type: ConnectorType

    /**
     * The network interface this host binds to as an IP address or a hostname.  If null or 0.0.0.0, then bind to all interfaces.
     */
    val host: String

    /**
     * The port this application should be bound to.
     */
    val port: Int
}

/**
 * Represents an SSL connector configuration.
 */
interface EngineSSLConnectorConfig : EngineConnectorConfig {
    /**
     * KeyStore where a certificate is stored
     */
    val keyStore: KeyStore

    /**
     * File where the keystore is located
     */
    val keyStorePath: File?

    /**
     * TLS key alias
     */
    val keyAlias: String

    /**
     * Keystore password provider
     */
    val keyStorePassword: () -> CharArray

    /**
     * Private key password provider
     */
    val privateKeyPassword: () -> CharArray
}

/**
 * Adds a non-secure connector to this engine environment
 */
inline fun ApplicationEngineEnvironmentBuilder.connector(builder: EngineConnectorBuilder.() -> Unit) {
    connectors.add(EngineConnectorBuilder().apply(builder))
}

/**
 * Adds a secure connector to this engine environment
 */
inline fun ApplicationEngineEnvironmentBuilder.sslConnector(
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
open class EngineConnectorBuilder(override val type: ConnectorType = ConnectorType.HTTP) : EngineConnectorConfig {
    override var host: String = "0.0.0.0"
    override var port: Int = 80

    override fun toString(): String {
        return "${type.name} $host:$port"
    }
}

/**
 * Mutable implementation of EngineSSLConnectorConfig for building connectors programmatically
 */
class EngineSSLConnectorBuilder(
    override var keyStore: KeyStore,
    override var keyAlias: String,
    override var keyStorePassword: () -> CharArray,
    override val privateKeyPassword: () -> CharArray
) : EngineConnectorBuilder(ConnectorType.HTTPS), EngineSSLConnectorConfig {
    override var keyStorePath: File? = null
}
