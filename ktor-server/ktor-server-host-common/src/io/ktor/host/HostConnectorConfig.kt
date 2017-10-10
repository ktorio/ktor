package io.ktor.host

import java.io.*
import java.security.*

data class ConnectorType(val name: String) {
    companion object {
        val HTTP = ConnectorType("HTTP")
        val HTTPS = ConnectorType("HTTPS")
    }
}

interface HostConnectorConfig {
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

interface HostSSLConnectorConfig : HostConnectorConfig {
    val keyStore: KeyStore
    val keyStorePath: File?
    val keyAlias: String

    val keyStorePassword: () -> CharArray
    val privateKeyPassword: () -> CharArray
}

inline fun ApplicationHostEnvironmentBuilder.connector(builder: HostConnectorBuilder.() -> Unit) {
    connectors.add(HostConnectorBuilder().apply(builder))
}

inline fun ApplicationHostEnvironmentBuilder.sslConnector(keyStore: KeyStore, keyAlias: String, noinline keyStorePassword: () -> CharArray, noinline privateKeyPassword: () -> CharArray, builder: HostSSLConnectorBuilder.() -> Unit) {
    connectors.add(HostSSLConnectorBuilder(keyStore, keyAlias, keyStorePassword, privateKeyPassword).apply(builder))
}

open class HostConnectorBuilder(override val type: ConnectorType = ConnectorType.HTTP) : HostConnectorConfig {
    override var host: String = "0.0.0.0"
    override var port: Int = 80

    override fun toString(): String {
        return "${type.name} $host:$port"
    }
}

class HostSSLConnectorBuilder(override var keyStore: KeyStore,
                              override var keyAlias: String,
                              override var keyStorePassword: () -> CharArray,
                              override val privateKeyPassword: () -> CharArray) : HostConnectorBuilder(ConnectorType.HTTPS), HostSSLConnectorConfig {
    override var keyStorePath: File? = null
}
