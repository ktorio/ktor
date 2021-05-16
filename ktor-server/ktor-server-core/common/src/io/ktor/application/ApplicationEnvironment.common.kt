/*
* Copyright 2014-2021 JetBrains s.r.o and contributors. Use of this source code is governed by the Apache 2.0 license.
*/

package io.ktor.application

import io.ktor.config.*
import io.ktor.util.*
import kotlin.coroutines.*

/**
 * Represents an environment in which [Application] runs
 */
public expect interface ApplicationEnvironment {
    /**
     * Parent coroutine context for an application
     */
    public val parentCoroutineContext: CoroutineContext

    /**
     * Instance of [Logger] to be used for logging.
     */
    public val log: Logger

    /**
     * Configuration for the [Application]
     */
    public val config: ApplicationConfig

    /**
     * Provides events on Application lifecycle
     */
    public val monitor: ApplicationEvents

    /**
     * Application's root path (prefix, context path in servlet container).
     */
    public val rootPath: String

    /**
     * Indicates if development mode is enabled.
     */
    public val developmentMode: Boolean
}
