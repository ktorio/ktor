/*
 * Copyright 2014-2019 JetBrains s.r.o and contributors. Use of this source code is governed by the Apache 2.0 license.
 */

package io.ktor.server.jetty

import io.ktor.application.*
import io.ktor.server.engine.*
import kotlinx.coroutines.*
import org.eclipse.jetty.server.*
import java.util.concurrent.*

/**
 * [ApplicationEngine] base type for running in a standalone Jetty
 */
public open class JettyApplicationEngineBase @EngineAPI constructor(
    environment: ApplicationEngineEnvironment,
    configure: Configuration.() -> Unit
) : BaseApplicationEngine(environment) {

    /**
     * Jetty-specific engine configuration
     */
    public class Configuration : BaseApplicationEngine.Configuration() {
        /**
         * Property function that will be called during Jetty server initialization
         * with the server instance as receiver.
         */
        public var configureServer: Server.() -> Unit = {}
    }

    /**
     * Application engine configuration specifying engine-specific options such as parallelism level.
     */
    protected val configuration: Configuration = Configuration().apply(configure)

    private var cancellationDeferred: CompletableJob? = null

    /**
     * Jetty server instance being configuring and starting
     */
    protected val server: Server = Server().apply {
        configuration.configureServer(this)
        initializeServer(environment)
    }

    override fun start(wait: Boolean): JettyApplicationEngineBase {
        environment.start()

        server.start()
        cancellationDeferred = stopServerOnCancellation()
        if (wait) {
            server.join()
            stop(1, 5, TimeUnit.SECONDS)
        }
        return this
    }

    override fun stop(gracePeriodMillis: Long, timeoutMillis: Long) {
        cancellationDeferred?.complete()
        environment.monitor.raise(ApplicationStopPreparing, environment)
        server.stopTimeout = timeoutMillis
        server.stop()
        server.destroy()
        environment.stop()
    }

    override fun toString(): String {
        return "Jetty($environment)"
    }
}
