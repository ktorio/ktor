/*
 * Copyright 2014-2024 JetBrains s.r.o and contributors. Use of this source code is governed by the Apache 2.0 license.
 */

package io.ktor.server.application

import io.ktor.events.*
import io.ktor.server.config.*
import io.ktor.util.logging.*
import kotlin.coroutines.*

public actual interface ApplicationEnvironment {

    /**
     * Configuration for the [Application]
     *
     * [Report a problem](https://ktor.io/feedback/?fqname=io.ktor.server.application.ApplicationEnvironment.config)
     */
    public actual val config: ApplicationConfig

    /**
     * Instance of [Logger] to be used for logging.
     *
     * [Report a problem](https://ktor.io/feedback/?fqname=io.ktor.server.application.ApplicationEnvironment.log)
     */
    public actual val log: Logger

    /**
     * Provides events on Application lifecycle
     *
     * [Report a problem](https://ktor.io/feedback/?fqname=io.ktor.server.application.ApplicationEnvironment.monitor)
     */
    @Deprecated(
        message = "Moved to Application",
        replaceWith = ReplaceWith("EmbeddedServer.monitor", "io.ktor.server.engine.EmbeddedServer"),
        level = DeprecationLevel.WARNING,
    )
    public actual val monitor: Events
}

internal actual class ApplicationRootConfigBridge actual constructor(
    rootConfig: ServerConfig,
    internal actual val parentCoroutineContext: CoroutineContext,
)
