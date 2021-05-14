/*
* Copyright 2014-2021 JetBrains s.r.o and contributors. Use of this source code is governed by the Apache 2.0 license.
*/

package io.ktor.server.engine

import io.ktor.application.*
import io.ktor.config.*
import io.ktor.util.*
import org.slf4j.*
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
@Suppress("MemberVisibilityCanBePrivate")
public class ApplicationEngineEnvironmentBuilder {
    /**
     * Parent coroutine context for an application
     */
    public var parentCoroutineContext: CoroutineContext = EmptyCoroutineContext

    /**
     * Paths to wait for application reload
     */
    public var watchPaths: List<String> = listOf(WORKING_DIRECTORY_PATH)

    /**
     * Root class loader
     */
    public var classLoader: ClassLoader = ApplicationEngineEnvironment::class.java.classLoader

    /**
     * Application logger
     */
    public var log: Logger = LoggerFactory.getLogger("Application")

    /**
     * Application config
     */
    public var config: ApplicationConfig = MapApplicationConfig()

    /**
     * Application connectors list
     */
    public val connectors: MutableList<EngineConnectorConfig> = mutableListOf()

    /**
     * Application modules
     */
    public val modules: MutableList<Application.() -> Unit> = mutableListOf()

    /**
     * Application's root path (prefix, context path in servlet container).
     */
    public var rootPath: String = ""

    /**
     * Development mode enabled.
     */
    public var developmentMode: Boolean = PlatformUtils.IS_DEVELOPMENT_MODE

    /**
     * Install application module
     */
    public fun module(body: Application.() -> Unit) {
        modules.add(body)
    }

    /**
     * Build an application engine environment
     */
    public fun build(builder: ApplicationEngineEnvironmentBuilder.() -> Unit): ApplicationEngineEnvironment {
        builder(this)
        return ApplicationEngineEnvironmentReloading(
            classLoader, log, config, connectors, modules, watchPaths,
            parentCoroutineContext, rootPath, developmentMode
        )
    }
}
