package io.ktor.server.engine

import java.io.*
import java.security.*

data class ConnectorType(val name: String) {
    companion object {
        val HTTP = ConnectorType("HTTP")
        val HTTPS = ConnectorType("HTTPS")
    }
}

interface EngineConnectorConfig {
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

interface EngineSSLConnectorConfig : EngineConnectorConfig {
    val keyStore: KeyStore
    val keyStorePath: File?
    val keyAlias: String

    val keyStorePassword: () -> CharArray
    val privateKeyPassword: () -> CharArray
}

inline fun ApplicationEngineEnvironmentBuilder.connector(builder: EngineConnectorBuilder.() -> Unit) {
    connectors.add(EngineConnectorBuilder().apply(builder))
}

inline fun ApplicationEngineEnvironmentBuilder.sslConnector(keyStore: KeyStore, keyAlias: String, noinline keyStorePassword: () -> CharArray, noinline privateKeyPassword: () -> CharArray, builder: EngineSSLConnectorBuilder.() -> Unit) {
    connectors.add(EngineSSLConnectorBuilder(keyStore, keyAlias, keyStorePassword, privateKeyPassword).apply(builder))
}

open class EngineConnectorBuilder(override val type: ConnectorType = ConnectorType.HTTP) : EngineConnectorConfig {
    override var host: String = "0.0.0.0"
    override var port: Int = 80

    override fun toString(): String {
        return "${type.name} $host:$port"
    }
}

class EngineSSLConnectorBuilder(override var keyStore: KeyStore,
                                override var keyAlias: String,
                                override var keyStorePassword: () -> CharArray,
                                override val privateKeyPassword: () -> CharArray) : EngineConnectorBuilder(ConnectorType.HTTPS), EngineSSLConnectorConfig {
    override var keyStorePath: File? = null
}
