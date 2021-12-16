/*
 * Copyright 2014-2021 JetBrains s.r.o and contributors. Use of this source code is governed by the Apache 2.0 license.
 */

package io.ktor.server.application

import io.ktor.events.*
import io.ktor.server.config.*
import io.ktor.util.date.*
import io.ktor.util.logging.*
import kotlin.coroutines.*

/**
 * Represents an environment in which [Application] runs
 */
public actual interface ApplicationEnvironment {

    /**
     * [ClassLoader] used to load application.
     *
     * Useful for various reflection-based services, like dependency injection.
     */
    public val classLoader: ClassLoader

    /**
     * Parent coroutine context for an application
     */
    public actual val parentCoroutineContext: CoroutineContext

    /**
     * Instance of [Logger] to be used for logging.
     */
    public actual val log: Logger

    /**
     * Configuration for the [Application]
     */
    public actual val config: ApplicationConfig

    /**
     * Provides events on Application lifecycle
     */
    public actual val monitor: Events

    /**
     * Application's root path (prefix, context path in servlet container).
     */
    public actual val rootPath: String

    /**
     * Indicates if development mode is enabled.
     */
    public actual val developmentMode: Boolean

    /**
     * Provides the current GMTDate. Use [GMTClock.System] in production code, which is used by default.
     */
    public actual val clock: GMTClock
}
