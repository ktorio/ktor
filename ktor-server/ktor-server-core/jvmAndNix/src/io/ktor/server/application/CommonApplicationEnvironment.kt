// ktlint-disable filename
/*
* Copyright 2014-2021 JetBrains s.r.o and contributors. Use of this source code is governed by the Apache 2.0 license.
*/

package io.ktor.server.application

import io.ktor.events.*
import io.ktor.server.config.*
import io.ktor.util.logging.*
import kotlin.coroutines.*

@Deprecated(message = "Renamed to ServerEnvironment", replaceWith = ReplaceWith("ServerEnvironment"))
public typealias ApplicationEnvironment = ServerEnvironment

/**
 * Represents an environment in which [Server] runs
 */
public expect interface ServerEnvironment {

    /**
     * Instance of [Logger] to be used for logging.
     */
    public val log: Logger

    /**
     * Configuration for the [Server]
     */
    public val config: ServerConfig

    /**
     * Provides events on Application lifecycle
     */
    @Deprecated(
        message = "Moved to Server",
        replaceWith = ReplaceWith("EmbeddedServer.monitor", "io.ktor.server.engine.EmbeddedServer"),
        level = DeprecationLevel.ERROR,
    )
    public val monitor: Events
}

internal expect class ServerParametersBridge(
    serverParameters: ServerParameters,
    parentCoroutineContext: CoroutineContext
) {
    internal val parentCoroutineContext: CoroutineContext
}
