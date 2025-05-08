/*
* Copyright 2014-2021 JetBrains s.r.o and contributors. Use of this source code is governed by the Apache 2.0 license.
*/

package io.ktor.server.cio

import io.ktor.network.sockets.*
import kotlinx.coroutines.*

/**
 * Represents a server instance
 *
 * [Report a problem](https://ktor.io/feedback/?fqname=io.ktor.server.cio.HttpServer)
 *
 * @property rootServerJob server job - root for all jobs
 * @property acceptJob client connections accepting job
 * @property serverSocket a deferred server socket instance, could be completed with error if it failed to bind
 */
public class HttpServer(
    public val rootServerJob: Job,
    public val acceptJob: Job,
    public val serverSocket: Deferred<ServerSocket>
)

/**
 * HTTP server connector settings
 *
 * [Report a problem](https://ktor.io/feedback/?fqname=io.ktor.server.cio.HttpServerSettings)
 *
 * @property host to listen to
 * @property port to listen to
 * @property connectionIdleTimeoutSeconds time to live for IDLE connections
 * @property reuseAddress allow the server to bind to an address that is already in use
 */
public open class HttpServerSettings(
    public val host: String = "0.0.0.0",
    public val port: Int = 8080,
    public val connectionIdleTimeoutSeconds: Long = 45,
    public val reuseAddress: Boolean = false
) {
    override fun toString(): String = "HttpServerSettings($host:$port)"
}

/**
 * Represents the settings for a Unix-based HTTP server.
 *
 * This class extends [HttpServerSettings] and overrides the `host` and `port`
 * properties to configure a Unix socket-based server. The server listens only on a
 * local interface and does not use a traditional TCP port. The Unix domain socket
 * path is specified by the [socketPath] parameter.
 *
 * @property socketPath the path to the Unix domain socket file used for the server communication.
 */
public class UnixHttpServerSettings(
    public val socketPath: String,
    connectionIdleTimeoutSeconds: Long,
    reuseAddress: Boolean
) : HttpServerSettings(
    host = "127.0.0.1",
    port = -1,
    reuseAddress = true,
    connectionIdleTimeoutSeconds = connectionIdleTimeoutSeconds
)
