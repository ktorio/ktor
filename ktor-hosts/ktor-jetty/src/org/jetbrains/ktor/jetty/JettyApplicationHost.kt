package org.jetbrains.ktor.jetty

import kotlinx.coroutines.experimental.*
import org.eclipse.jetty.server.*
import org.jetbrains.ktor.host.*
import org.jetbrains.ktor.util.*
import java.util.concurrent.*

/**
 * [ApplicationHost] implementation for running standalone Jetty Host
 */
class JettyApplicationHost(environment: ApplicationHostEnvironment,
                           jettyServer: () -> Server = ::Server) : JettyApplicationHostBase(environment, jettyServer) {

    private val dispatcher = DispatcherWithShutdown(server.threadPool.asCoroutineDispatcher())

    override fun start(wait: Boolean) : JettyApplicationHost {
        server.handler = JettyKtorHandler(environment, this::pipeline, dispatcher)
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
