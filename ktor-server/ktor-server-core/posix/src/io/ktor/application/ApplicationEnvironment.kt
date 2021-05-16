/*
 * Copyright 2014-2021 JetBrains s.r.o and contributors. Use of this source code is governed by the Apache 2.0 license.
 */

package io.ktor.application

import io.ktor.*
import io.ktor.config.*
import io.ktor.util.*
import kotlin.coroutines.*

/**
 * Represents an environment in which [Application] runs
 */
public actual interface ApplicationEnvironment {
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
    public actual val monitor: ApplicationEvents

    /**
     * Application's root path (prefix, context path in servlet container).
     */
    public actual val rootPath: String

    /**
     * Indicates if development mode is enabled.
     */
    public actual val developmentMode: Boolean

}
