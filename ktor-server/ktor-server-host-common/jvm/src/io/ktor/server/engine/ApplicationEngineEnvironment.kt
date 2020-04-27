/*
 * Copyright 2014-2019 JetBrains s.r.o and contributors. Use of this source code is governed by the Apache 2.0 license.
 */

package io.ktor.server.engine

import io.ktor.application.*
import io.ktor.config.*
import io.ktor.util.*
import org.slf4j.*
import kotlin.coroutines.*

/**
 * Represents an environment in which engine runs
 */
interface ApplicationEngineEnvironment : ApplicationEnvironment {
    /**
     * Connectors that describers where and how server should listen
     */
    val connectors: List<EngineConnectorConfig>

    /**
     * Running [Application]
     *
     * Throws an exception if environment has not been started
     */
    val application: Application

    /**
     * Starts [ApplicationEngineEnvironment] and creates an application
     */
    fun start()

    /**
     * Stops [ApplicationEngineEnvironment] and destroys any running application
     */
    fun stop()
}

/**
 * Creates [ApplicationEngineEnvironment] using [ApplicationEngineEnvironmentBuilder]
 */
fun applicationEngineEnvironment(builder: ApplicationEngineEnvironmentBuilder.() -> Unit): ApplicationEngineEnvironment {
    return ApplicationEngineEnvironmentBuilder().build(builder)
}

/**
 * Engine environment configuration builder
 */
@Suppress("MemberVisibilityCanBePrivate")
class ApplicationEngineEnvironmentBuilder {
    /**
     * Parent coroutine context for an application
     */
    var parentCoroutineContext: CoroutineContext = EmptyCoroutineContext

    /**
     * Paths to wait for application reload
     */
    var watchPaths: List<String> = emptyList()

    /**
     * Root class loader
     */
    var classLoader: ClassLoader = ApplicationEngineEnvironment::class.java.classLoader

    /**
     * Application logger
     */
    var log: Logger = LoggerFactory.getLogger("Application")

    /**
     * Application config
     */
    var config: ApplicationConfig = MapApplicationConfig()

    /**
     * Application connectors list
     */
    val connectors: MutableList<EngineConnectorConfig> = mutableListOf()

    /**
     * Application modules
     */
    val modules: MutableList<Application.() -> Unit> = mutableListOf()

    /**
     * Application's root path (prefix, context path in servlet container).
     */
    @KtorExperimentalAPI
    var rootPath: String = ""

    /**
     * Install application module
     */
    fun module(body: Application.() -> Unit) {
        modules.add(body)
    }

    /**
     * Build an application engine environment
     */
    fun build(builder: ApplicationEngineEnvironmentBuilder.() -> Unit): ApplicationEngineEnvironment {
        builder(this)
        return ApplicationEngineEnvironmentReloading(
            classLoader, log, config, connectors, modules, watchPaths,
            parentCoroutineContext, rootPath
        )
    }
}
