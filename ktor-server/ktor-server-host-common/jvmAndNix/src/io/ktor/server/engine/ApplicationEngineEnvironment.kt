/*
* Copyright 2014-2021 JetBrains s.r.o and contributors. Use of this source code is governed by the Apache 2.0 license.
*/

package io.ktor.server.engine

import io.ktor.server.application.*
import io.ktor.server.config.*
import io.ktor.util.*
import io.ktor.util.logging.*
import io.ktor.util.network.*
import kotlinx.coroutines.*
import kotlin.coroutines.*

/**
 * Represents an environment in which engine runs.
 */
public interface ApplicationEngineEnvironment : ApplicationEnvironment {
    /**
     * Connectors that describers where and how server should listen.
     */
    public val connectors: List<EngineConnectorConfig>

    /**
     * Running [Application].
     *
     * @throws an exception if environment has not been started.
     */
    public val application: Application

    /**
     * Starts [ApplicationEngineEnvironment] and creates an application.
     */
    public fun start()

    /**
     * Stops [ApplicationEngineEnvironment] and destroys any running application.
     */
    public fun stop()
}

/**
 * Creates [ApplicationEngineEnvironment] using [ApplicationEngineEnvironmentBuilder].
 */
public fun applicationEngineEnvironment(
    builder: ApplicationEngineEnvironmentBuilder.() -> Unit
): ApplicationEngineEnvironment {
    return ApplicationEngineEnvironmentBuilder().build(builder)
}

/**
 * Engine environment configuration builder
 */
@KtorDsl
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
