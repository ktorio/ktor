// ktlint-disable filename
/*
 * Copyright 2014-2021 JetBrains s.r.o and contributors. Use of this source code is governed by the Apache 2.0 license.
 */

package io.ktor.server.application

import io.ktor.events.*
import io.ktor.server.config.*
import io.ktor.util.logging.*
import kotlin.coroutines.*

public actual interface ServerEnvironment {

    /**
     * Configuration for the [Server]
     */
    public actual val config: ServerConfig

    /**
     * Instance of [Logger] to be used for logging.
     */
    public actual val log: Logger

    /**
     * Provides events on Application lifecycle
     */
    @Deprecated(
        message = "Moved to Application",
        replaceWith = ReplaceWith("EmbeddedServer.monitor", "io.ktor.server.engine.EmbeddedServer"),
        level = DeprecationLevel.WARNING,
    )
    public actual val monitor: Events
}

internal actual class ServerParametersBridge actual constructor(
    serverParameters: ServerParameters,
    internal actual val parentCoroutineContext: CoroutineContext,
)
