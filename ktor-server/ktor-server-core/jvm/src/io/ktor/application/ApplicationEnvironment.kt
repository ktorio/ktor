/*
 * Copyright 2014-2019 JetBrains s.r.o and contributors. Use of this source code is governed by the Apache 2.0 license.
 */

package io.ktor.application

import io.ktor.config.*
import io.ktor.util.*
import io.ktor.util.logging.*
import kotlin.coroutines.*

/**
 * Represents an environment in which [Application] runs
 */
interface ApplicationEnvironment {
    /**
     * Parent coroutine context for an application
     */
    val parentCoroutineContext: CoroutineContext

    /**
     * [ClassLoader] used to load application.
     *
     * Useful for various reflection-based services, like dependency injection.
     */
    val classLoader: ClassLoader

    /**
     * Instance of [Logger] to be used for logging.
     */
    val log: Logger

    /**
     * Configuration for the [Application]
     */
    val config: ApplicationConfig

    /**
     * Provides events on Application lifecycle
     */
    val monitor: ApplicationEvents

    /**
     * Application's root path (prefix, context path in servlet container).
     */
    @KtorExperimentalAPI
    val rootPath: String
}
