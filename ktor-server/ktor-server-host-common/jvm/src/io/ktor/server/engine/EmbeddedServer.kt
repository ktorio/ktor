/*
 * Copyright 2014-2019 JetBrains s.r.o and contributors. Use of this source code is governed by the Apache 2.0 license.
 */

package io.ktor.server.engine

import io.ktor.application.*
import io.ktor.util.logging.*
import io.ktor.util.logging.labels.*
import kotlinx.coroutines.*
import kotlin.coroutines.*

/**
 * Factory interface for creating [ApplicationEngine] instances
 */
interface ApplicationEngineFactory<out TEngine : ApplicationEngine, TConfiguration : ApplicationEngine.Configuration> {
    /**
     * Creates an engine from the given [environment] and [configure] script
     */
    fun create(environment: ApplicationEngineEnvironment, configure: TConfiguration.() -> Unit): TEngine
}

/**
 * Creates an embedded server with the given [factory], listening on [host]:[port]
 * @param watchPaths specifies path substrings that will be watched for automatic reloading
 * @param configure configuration script for the engine
 * @param module application module function
 */
fun <TEngine : ApplicationEngine, TConfiguration : ApplicationEngine.Configuration>
    embeddedServer(
    factory: ApplicationEngineFactory<TEngine, TConfiguration>,
    port: Int = 80,
    host: String = "0.0.0.0",
    watchPaths: List<String> = emptyList(),
    configure: TConfiguration.() -> Unit = {},
    module: Application.() -> Unit
): TEngine = GlobalScope.embeddedServer(factory, port, host, watchPaths, EmptyCoroutineContext, configure, module)

/**
 * Creates an embedded server with the given [factory], listening on [host]:[port]
 * @param watchPaths specifies path substrings that will be watched for automatic reloading
 * @param configure configuration script for the engine
 * @param parentCoroutineContext specifies a coroutine context to be used for server jobs
 * @param module application module function
 */
fun <TEngine : ApplicationEngine, TConfiguration : ApplicationEngine.Configuration>
    CoroutineScope.embeddedServer(
    factory: ApplicationEngineFactory<TEngine, TConfiguration>,
    port: Int = 80,
    host: String = "0.0.0.0",
    watchPaths: List<String> = emptyList(),
    parentCoroutineContext: CoroutineContext = EmptyCoroutineContext,
    configure: TConfiguration.() -> Unit = {},
    module: Application.() -> Unit
): TEngine {
    val environment = applicationEngineEnvironment {
        this.parentCoroutineContext = coroutineContext + parentCoroutineContext
        this.log = logger().addName("ktor.application")
        this.watchPaths = watchPaths
        this.module(module)

        connector {
            this.port = port
            this.host = host
        }
    }

    return embeddedServer(factory, environment, configure)
}

/**
 * Creates an embedded server with the given [factory], [environment] and [configure] script
 */
fun <TEngine : ApplicationEngine, TConfiguration : ApplicationEngine.Configuration>
    embeddedServer(
    factory: ApplicationEngineFactory<TEngine, TConfiguration>,
    environment: ApplicationEngineEnvironment,
    configure: TConfiguration.() -> Unit = {}
): TEngine {
    return factory.create(environment, configure)
}

