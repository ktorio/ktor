package io.ktor.server.jetty

import io.ktor.server.engine.*
import io.ktor.util.*
import kotlinx.coroutines.*
import java.util.concurrent.*

/**
 * [ApplicationEngine] implementation for running in a standalone Jetty
 */
class JettyApplicationEngine(
    environment: ApplicationEngineEnvironment, configure: Configuration.() -> Unit
) : JettyApplicationEngineBase(environment, configure) {

    private val dispatcher = DispatcherWithShutdown(server.threadPool.asCoroutineDispatcher())

    override fun start(wait: Boolean): JettyApplicationEngine {
        server.handler = JettyKtorHandler(environment, this::pipeline, dispatcher, configuration)
        super.start(wait)
        return this
    }

    override fun stop(gracePeriod: Long, timeout: Long, timeUnit: TimeUnit) {
        dispatcher.prepareShutdown()
        try {
            super.stop(gracePeriod, timeout, timeUnit)
        } finally {
            dispatcher.completeShutdown()
        }
    }
}
