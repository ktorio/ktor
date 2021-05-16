/*
 * Copyright 2014-2021 JetBrains s.r.o and contributors. Use of this source code is governed by the Apache 2.0 license.
 */

package io.ktor.server.engine

import io.ktor.*
import io.ktor.application.*
import io.ktor.config.*
import io.ktor.util.*
import kotlin.coroutines.*

/**
 * Engine environment configuration builder
 */
@Suppress("MemberVisibilityCanBePrivate")
public expect class ApplicationEngineEnvironmentBuilder() {
    /**
     * Parent coroutine context for an application
     */
    public var parentCoroutineContext: CoroutineContext

    /**
     * Paths to wait for application reload
     */
    public var watchPaths: List<String>

    /**
     * Application logger
     */
    public var log: Logger

    /**
     * Application config
     */
    public var config: ApplicationConfig

    /**
     * Application connectors list
     */
    public val connectors: MutableList<EngineConnectorConfig>

    /**
     * Application modules
     */
    public val modules: MutableList<Application.() -> Unit>

    /**
     * Application's root path (prefix, context path in servlet container).
     */
    public var rootPath: String

    /**
     * Development mode enabled.
     */
    public var developmentMode: Boolean

    /**
     * Install application module
     */
    public fun module(body: Application.() -> Unit)

    /**
     * Build an application engine environment
     */
    public fun build(builder: ApplicationEngineEnvironmentBuilder.() -> Unit): ApplicationEngineEnvironment
}
