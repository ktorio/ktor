/*
 * Copyright 2014-2019 JetBrains s.r.o and contributors. Use of this source code is governed by the Apache 2.0 license.
 */

package io.ktor.server.engine

import io.ktor.events.*
import io.ktor.server.application.*
import io.ktor.util.logging.*
import kotlinx.coroutines.*
import kotlin.coroutines.*

public expect class EmbeddedServer<TEngine : ApplicationEngine, TConfiguration : ApplicationEngine.Configuration>(
    applicationProperties: ApplicationProperties,
    engineFactory: ApplicationEngineFactory<TEngine, TConfiguration>,
    engineConfigBlock: TConfiguration.() -> Unit = {}
) {
    /**
     * Provides events on Application lifecycle
     */
    public val monitor: Events

    public val environment: ApplicationEnvironment
    public val application: Application
    public val engine: TEngine
    public val engineConfig: TConfiguration

    public fun start(wait: Boolean = false): EmbeddedServer<TEngine, TConfiguration>

    public fun stop(
        gracePeriodMillis: Long = engineConfig.shutdownGracePeriod,
        timeoutMillis: Long = engineConfig.shutdownGracePeriod
    )
}

/**
 * Factory interface for creating [ApplicationEngine] instances
 */
public interface ApplicationEngineFactory<
    out TEngine : ApplicationEngine,
    TConfiguration : ApplicationEngine.Configuration
    > {

    public fun configuration(configure: TConfiguration.() -> Unit): TConfiguration

    /**
     * Creates an engine from the given [environment] and [configure] script
     */
    public fun create(
        environment: ApplicationEnvironment,
        monitor: Events,
        developmentMode: Boolean,
        configuration: TConfiguration,
        applicationProvider: () -> Application
    ): TEngine
}

/**
 * Creates an embedded server with the given [factory], listening on [host]:[port]
 * @param watchPaths specifies path substrings that will be watched for automatic reloading
 * @param configure configuration script for the engine
 * @param module application module function
 */
@OptIn(DelicateCoroutinesApi::class)
public fun <TEngine : ApplicationEngine, TConfiguration : ApplicationEngine.Configuration>
    embeddedServer(
    factory: ApplicationEngineFactory<TEngine, TConfiguration>,
    port: Int = 80,
    host: String = "0.0.0.0",
    watchPaths: List<String> = listOf(WORKING_DIRECTORY_PATH),
    module: Application.() -> Unit
): EmbeddedServer<TEngine, TConfiguration> =
    GlobalScope.embeddedServer(factory, port, host, watchPaths, module = module)

/**
 * Creates an embedded server with the given [factory], listening on [host]:[port]
 * @param watchPaths specifies path substrings that will be watched for automatic reloading
 * @param configure configuration script for the engine
 * @param parentCoroutineContext specifies a coroutine context to be used for server jobs
 * @param module application module function
 */
public fun <TEngine : ApplicationEngine, TConfiguration : ApplicationEngine.Configuration>
    CoroutineScope.embeddedServer(
    factory: ApplicationEngineFactory<TEngine, TConfiguration>,
    port: Int = 80,
    host: String = "0.0.0.0",
    watchPaths: List<String> = listOf(WORKING_DIRECTORY_PATH),
    parentCoroutineContext: CoroutineContext = EmptyCoroutineContext,
    module: Application.() -> Unit
): EmbeddedServer<TEngine, TConfiguration> {
    val connectors: Array<EngineConnectorConfig> = arrayOf(
        EngineConnectorBuilder().apply {
            this.port = port
            this.host = host
        }
    )
    return embeddedServer(
        factory = factory,
        connectors = connectors,
        watchPaths = watchPaths,
        parentCoroutineContext = parentCoroutineContext,
        module = module
    )
}

/**
 * Creates an embedded server with the given [factory], listening on given [connectors]
 * @param connectors default listening on 0.0.0.0:80
 * @param watchPaths specifies path substrings that will be watched for automatic reloading
 * @param parentCoroutineContext specifies a coroutine context to be used for server jobs
 * @param configure configuration script for the engine
 * @param module application module function
 */
public fun <TEngine : ApplicationEngine, TConfiguration : ApplicationEngine.Configuration>
    CoroutineScope.embeddedServer(
    factory: ApplicationEngineFactory<TEngine, TConfiguration>,
    vararg connectors: EngineConnectorConfig = arrayOf(),
    watchPaths: List<String> = listOf(WORKING_DIRECTORY_PATH),
    parentCoroutineContext: CoroutineContext = EmptyCoroutineContext,
    module: Application.() -> Unit
): EmbeddedServer<TEngine, TConfiguration> {
    val environment = applicationEnvironment {
        this.log = KtorSimpleLogger("io.ktor.server.Application")
    }
    val applicationProperties = applicationProperties(environment) {
        this.parentCoroutineContext = coroutineContext + parentCoroutineContext
        this.watchPaths = watchPaths
        this.module(module)
    }
    val config: TConfiguration.() -> Unit = {
        this.connectors.addAll(connectors)
    }

    return embeddedServer(factory, applicationProperties, config)
}

/**
 * Creates an embedded server with the given [factory], [environment] and [configure] script
 */
public fun <TEngine : ApplicationEngine, TConfiguration : ApplicationEngine.Configuration> embeddedServer(
    factory: ApplicationEngineFactory<TEngine, TConfiguration>,
    environment: ApplicationEnvironment = applicationEnvironment(),
    configure: TConfiguration.() -> Unit = {},
    module: Application.() -> Unit = {}
): EmbeddedServer<TEngine, TConfiguration> {
    val applicationProperties = applicationProperties(environment) {
        module(body = module)
    }
    return embeddedServer(factory, applicationProperties, configure)
}

/**
 * Creates an embedded server with the given [factory], [environment] and [configure] script
 */
public fun <TEngine : ApplicationEngine, TConfiguration : ApplicationEngine.Configuration> embeddedServer(
    factory: ApplicationEngineFactory<TEngine, TConfiguration>,
    applicationProperties: ApplicationProperties,
    configure: TConfiguration.() -> Unit = {}
): EmbeddedServer<TEngine, TConfiguration> {
    return EmbeddedServer(applicationProperties, factory, configure)
}
