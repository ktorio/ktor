/*
 * Copyright 2014-2019 JetBrains s.r.o and contributors. Use of this source code is governed by the Apache 2.0 license.
 */

package io.ktor.server.engine

import io.ktor.events.*
import io.ktor.server.application.*
import io.ktor.util.logging.*
import kotlinx.coroutines.*
import kotlin.coroutines.*

/**
 * Represents an embedded server that hosts an application.
 * It's an entry point to the application and handles the lifecycle of the application engine.
 *
 * @param TEngine The type of the application engine used by the server.
 * @param TConfiguration The type of the configuration used by the engine.
 */
public expect class EmbeddedServer<TEngine : ServerEngine, TConfiguration : ServerEngine.Configuration>(
    serverParameters: ServerParameters,
    engineFactory: ServerEngineFactory<TEngine, TConfiguration>,
    engineConfigBlock: TConfiguration.() -> Unit = {}
) {
    /**
     * Provides events on Application lifecycle
     */
    public val monitor: Events

    public val environment: ServerEnvironment
    public val server: Server
    public val engine: TEngine
    public val engineConfig: TConfiguration

    public fun start(wait: Boolean = false): EmbeddedServer<TEngine, TConfiguration>

    public fun stop(
        gracePeriodMillis: Long = engineConfig.shutdownGracePeriod,
        timeoutMillis: Long = engineConfig.shutdownGracePeriod
    )
}

@Deprecated(message = "Renamed to ServerEngineFactory", replaceWith = ReplaceWith("ServerEngineFactory"))
public typealias ApplicationEngineFactory<E, C> = ServerEngineFactory<E, C>

/**
 * Factory interface for creating [ServerEngine] instances.
 */
public interface ServerEngineFactory<
    out TEngine : ServerEngine,
    TConfiguration : ServerEngine.Configuration
    > {

    public fun configuration(configure: TConfiguration.() -> Unit): TConfiguration

    /**
     * Creates an engine from the given [environment] and [configuration].
     */
    public fun create(
        environment: ServerEnvironment,
        monitor: Events,
        developmentMode: Boolean,
        configuration: TConfiguration,
        serverProvider: () -> Server
    ): TEngine
}

/**
 * Creates an embedded server with the given [factory], listening on [host]:[port].
 */
@OptIn(DelicateCoroutinesApi::class)
public fun <TEngine : ServerEngine, TConfiguration : ServerEngine.Configuration> embeddedServer(
    factory: ServerEngineFactory<TEngine, TConfiguration>,
    port: Int = 80,
    host: String = "0.0.0.0",
    watchPaths: List<String> = listOf(WORKING_DIRECTORY_PATH),
    module: Server.() -> Unit
): EmbeddedServer<TEngine, TConfiguration> =
    GlobalScope.embeddedServer(factory, port, host, watchPaths, module = module)

/**
 * Creates an embedded server with the given [factory], listening on [host]:[port].
 */
public fun <TEngine : ServerEngine, TConfiguration : ServerEngine.Configuration> CoroutineScope.embeddedServer( // ktlint-disable max-line-length
    factory: ServerEngineFactory<TEngine, TConfiguration>,
    port: Int = 80,
    host: String = "0.0.0.0",
    watchPaths: List<String> = listOf(WORKING_DIRECTORY_PATH),
    parentCoroutineContext: CoroutineContext = EmptyCoroutineContext,
    module: Server.() -> Unit
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
 * Creates an embedded server with the given [factory], listening on given [connectors].
 */
public fun <TEngine : ServerEngine, TConfiguration : ServerEngine.Configuration> CoroutineScope.embeddedServer( // ktlint-disable max-line-length
    factory: ServerEngineFactory<TEngine, TConfiguration>,
    vararg connectors: EngineConnectorConfig = arrayOf(),
    watchPaths: List<String> = listOf(WORKING_DIRECTORY_PATH),
    parentCoroutineContext: CoroutineContext = EmptyCoroutineContext,
    module: Server.() -> Unit
): EmbeddedServer<TEngine, TConfiguration> {
    val environment = serverEnvironment {
        this.log = KtorSimpleLogger("io.ktor.server.Application")
    }
    val applicationProperties = serverParams(environment) {
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
 * Creates an embedded server with the given [factory], [environment] and [configure] script.
 */
public fun <TEngine : ServerEngine, TConfiguration : ServerEngine.Configuration> embeddedServer(
    factory: ServerEngineFactory<TEngine, TConfiguration>,
    environment: ServerEnvironment = serverEnvironment(),
    configure: TConfiguration.() -> Unit = {},
    module: Server.() -> Unit = {}
): EmbeddedServer<TEngine, TConfiguration> {
    val applicationProperties = serverParams(environment) {
        module(body = module)
    }
    return embeddedServer(factory, applicationProperties, configure)
}

/**
 * Creates an embedded server with the given [factory], [environment] and [configure] script.
 */
public fun <TEngine : ServerEngine, TConfiguration : ServerEngine.Configuration> embeddedServer(
    factory: ServerEngineFactory<TEngine, TConfiguration>,
    serverParameters: ServerParameters,
    configure: TConfiguration.() -> Unit = {}
): EmbeddedServer<TEngine, TConfiguration> {
    return EmbeddedServer(serverParameters, factory, configure)
}
