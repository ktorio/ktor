/*
 * Copyright 2014-2023 JetBrains s.r.o and contributors. Use of this source code is governed by the Apache 2.0 license.
 */

package io.ktor.server.jetty.jakarta

import io.ktor.events.*
import io.ktor.server.application.*
import io.ktor.server.engine.*
import kotlinx.coroutines.CompletableJob
import org.eclipse.jetty.server.Server
import org.eclipse.jetty.server.ServerConnector
import kotlin.time.Duration
import kotlin.time.Duration.Companion.seconds

/**
 * [ApplicationEngine] base type for running in a standalone Jetty
 *
 * [Report a problem](https://ktor.io/feedback/?fqname=io.ktor.server.jetty.jakarta.JettyApplicationEngineBase)
 */
public open class JettyApplicationEngineBase(
    environment: ApplicationEnvironment,
    monitor: Events,
    developmentMode: Boolean,
    /**
     * Application engine configuration specifying engine-specific options such as parallelism level.
     */
    public val configuration: Configuration,
    private val applicationProvider: () -> Application
) : BaseApplicationEngine(environment, monitor, developmentMode) {

    /**
     * Jetty-specific engine configuration
     *
     * [Report a problem](https://ktor.io/feedback/?fqname=io.ktor.server.jetty.jakarta.JettyApplicationEngineBase.Configuration)
     */
    public class Configuration : BaseApplicationEngine.Configuration() {
        /**
         * Property function that will be called during Jetty server initialization
         * with the server instance as receiver.
         *
         * [Report a problem](https://ktor.io/feedback/?fqname=io.ktor.server.jetty.jakarta.JettyApplicationEngineBase.Configuration.configureServer)
         */
        public var configureServer: Server.() -> Unit = {}

        /**
         * The duration of time that a connection can be idle before the connector takes action to close the connection.
         *
         * [Report a problem](https://ktor.io/feedback/?fqname=io.ktor.server.jetty.jakarta.JettyApplicationEngineBase.Configuration.idleTimeout)
         */
        public var idleTimeout: Duration = 30.seconds
    }

    private var cancellationJob: CompletableJob? = null

    /**
     * Jetty server instance being configuring and starting
     */
    protected val server: Server = Server().apply {
        configuration.configureServer(this)
        initializeServer(configuration)
    }

    override fun start(wait: Boolean): JettyApplicationEngineBase {
        server.start()
        cancellationJob = stopServerOnCancellation(
            applicationProvider(),
            configuration.shutdownGracePeriod,
            configuration.shutdownTimeout
        )

        val connectors = server.connectors.zip(configuration.connectors)
            .map { it.second.withPort((it.first as ServerConnector).localPort) }
        resolvedConnectorsDeferred.complete(connectors)

        monitor.raiseCatching(ServerReady, environment, environment.log)

        if (wait) {
            server.join()
            stop(configuration.shutdownGracePeriod, configuration.shutdownTimeout)
        }
        return this
    }

    override fun stop(gracePeriodMillis: Long, timeoutMillis: Long) {
        cancellationJob?.complete()
        monitor.raise(ApplicationStopPreparing, environment)
        server.stopTimeout = timeoutMillis
        server.stop()
        server.destroy()
    }

    override fun toString(): String {
        return "Jetty($environment)"
    }
}
