/*
 * Copyright 2014-2025 JetBrains s.r.o and contributors. Use of this source code is governed by the Apache 2.0 license.
 */

package io.ktor.server.cio

import io.ktor.server.engine.*

/**
 * Represents a Unix domain socket connector configuration.
 *
 * [Report a problem](https://ktor.io/feedback/?fqname=io.ktor.server.cio.UnixSocketConnectorConfig)
 */
public interface UnixSocketConnectorConfig : EngineConnectorConfig {
    /**
     * Path to the Unix domain socket file.
     *
     * [Report a problem](https://ktor.io/feedback/?fqname=io.ktor.server.cio.UnixSocketConnectorConfig.socketPath)
     */
    public val socketPath: String
}

/**
 * Mutable implementation of UnixSocketConnectorConfig for building connectors programmatically
 *
 * [Report a problem](https://ktor.io/feedback/?fqname=io.ktor.server.cio.UnixSocketConnectorBuilder)
 */
public class UnixSocketConnectorBuilder : EngineConnectorBuilder(ConnectorType.UNIX), UnixSocketConnectorConfig {
    /**
     * Path to the Unix domain socket file.
     *
     * [Report a problem](https://ktor.io/feedback/?fqname=io.ktor.server.cio.UnixSocketConnectorBuilder.socketPath)
     */
    override var socketPath: String = "/tmp/ktor.sock"

    override fun toString(): String = "${type.name} $socketPath"
}

/**
 * Adds a Unix domain socket connector to this engine environment
 *
 * [Report a problem](https://ktor.io/feedback/?fqname=io.ktor.server.cio.unixConnector)
 */
public fun ApplicationEngine.Configuration.unixConnector(
    socketPath: String,
    builder: UnixSocketConnectorBuilder.() -> Unit = {}
) {
    val element = UnixSocketConnectorBuilder().apply {
        this.socketPath = socketPath
        builder()
    }
    connectors.add(element)
}
