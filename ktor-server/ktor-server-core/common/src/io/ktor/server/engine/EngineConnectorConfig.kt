/*
 * Copyright 2014-2019 JetBrains s.r.o and contributors. Use of this source code is governed by the Apache 2.0 license.
 */

package io.ktor.server.engine

/**
 * Represents a type of a connector, e.g HTTP or HTTPS.
 *
 * [Report a problem](https://ktor.io/feedback/?fqname=io.ktor.server.engine.ConnectorType)
 *
 * @param name name of the connector.
 *
 * Some engines can support other connector types, hence not a enum.
 */
public data class ConnectorType(val name: String) {
    public companion object {
        /**
         * Non-secure HTTP connector.
         *
         * [Report a problem](https://ktor.io/feedback/?fqname=io.ktor.server.engine.ConnectorType.Companion.HTTP)
         */
        public val HTTP: ConnectorType = ConnectorType("HTTP")

        /**
         * Secure HTTP connector.
         *
         * [Report a problem](https://ktor.io/feedback/?fqname=io.ktor.server.engine.ConnectorType.Companion.HTTPS)
         */
        public val HTTPS: ConnectorType = ConnectorType("HTTPS")
    }
}

/**
 * Represents a connector configuration.
 *
 * [Report a problem](https://ktor.io/feedback/?fqname=io.ktor.server.engine.EngineConnectorConfig)
 */
public interface EngineConnectorConfig {
    /**
     * Type of the connector, e.g HTTP or HTTPS.
     *
     * [Report a problem](https://ktor.io/feedback/?fqname=io.ktor.server.engine.EngineConnectorConfig.type)
     */
    public val type: ConnectorType

    /**
     * The network interface this host binds to as an IP address or a hostname. If null or 0.0.0.0, then bind to all interfaces.
     *
     * [Report a problem](https://ktor.io/feedback/?fqname=io.ktor.server.engine.EngineConnectorConfig.host)
     */
    public val host: String

    /**
     * The port this application should be bound to.
     *
     * [Report a problem](https://ktor.io/feedback/?fqname=io.ktor.server.engine.EngineConnectorConfig.port)
     */
    public val port: Int
}

/**
 * Adds a non-secure connector to this engine environment
 *
 * [Report a problem](https://ktor.io/feedback/?fqname=io.ktor.server.engine.connector)
 */
public inline fun ApplicationEngine.Configuration.connector(builder: EngineConnectorBuilder.() -> Unit) {
    connectors.add(EngineConnectorBuilder().apply(builder))
}

/**
 * Mutable implementation of EngineConnectorConfig for building connectors programmatically
 *
 * [Report a problem](https://ktor.io/feedback/?fqname=io.ktor.server.engine.EngineConnectorBuilder)
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
 * Returns new instance of [EngineConnectorConfig] based on [this] with modified port
 *
 * [Report a problem](https://ktor.io/feedback/?fqname=io.ktor.server.engine.withPort)
 */
public expect fun EngineConnectorConfig.withPort(otherPort: Int): EngineConnectorConfig
