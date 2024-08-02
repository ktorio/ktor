/*
 * Copyright 2014-2021 JetBrains s.r.o and contributors. Use of this source code is governed by the Apache 2.0 license.
 */

package io.ktor.server.engine

import io.ktor.events.*
import io.ktor.events.EventDefinition
import io.ktor.server.application.*
import io.ktor.server.engine.internal.*
import kotlinx.coroutines.*

public actual class EmbeddedServer<
    TEngine : ServerEngine,
    TConfiguration : ServerEngine.Configuration
    >
actual constructor(
    serverParameters: ServerParameters,
    engineFactory: ServerEngineFactory<TEngine, TConfiguration>,
    engineConfigBlock: TConfiguration.() -> Unit
) {
    public actual val monitor: Events = applicationProperties.environment.monitor

    public actual val environment: ServerEnvironment = serverParameters.environment

    public actual val engineConfig: TConfiguration = engineFactory.configuration(engineConfigBlock)

    public actual val server: Server = Server(
        environment,
        serverParameters.developmentMode,
        serverParameters.rootPath,
        monitor,
        serverParameters.parentCoroutineContext,
        ::engine
    )

    public actual val engine: TEngine = engineFactory.create(
        environment,
        monitor,
        serverParameters.developmentMode,
        engineConfig,
        ::server
    )

    private val modules = serverParameters.modules

    public actual fun start(wait: Boolean): EmbeddedServer<TEngine, TConfiguration> {
        safeRaiseEvent(ServerStarting, server)
        try {
            modules.forEach { server.it() }
        } catch (cause: Throwable) {
            environment.log.error("Failed to start application.", cause)
            destroy(server)
            throw cause
        }
        safeRaiseEvent(ServerStarted, server)

        CoroutineScope(server.coroutineContext).launch {
            engine.resolvedConnectors().forEach {
                val host = escapeHostname(it.host)
                environment.log.info(
                    "Responding at ${it.type.name.lowercase()}://$host:${it.port}"
                )
            }
        }

        engine.start(wait)

        return this
    }

    public actual fun stop(gracePeriodMillis: Long, timeoutMillis: Long) {
        engine.stop(gracePeriodMillis, timeoutMillis)
        destroy(server)
    }

    private fun destroy(server: Server) {
        safeRaiseEvent(ServerStopping, server)
        try {
            server.dispose()
        } catch (e: Throwable) {
            environment.log.error("Failed to destroy application instance.", e)
        }
        safeRaiseEvent(ServerStopped, server)
    }

    private fun safeRaiseEvent(event: EventDefinition<Server>, server: Server) {
        try {
            monitor.raise(event, server)
        } catch (cause: Throwable) {
            environment.log.error("One or more of the handlers thrown an exception", cause)
        }
    }
}
