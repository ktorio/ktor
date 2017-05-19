package org.jetbrains.ktor.jetty

import org.eclipse.jetty.server.*
import org.jetbrains.ktor.host.*
import java.util.concurrent.*

/**
 * [ApplicationHost] implementation for running standalone Jetty Host
 */
open class JettyApplicationHostBase(environment: ApplicationHostEnvironment,
                                    jettyServer: () -> Server = ::Server) : BaseApplicationHost(environment) {

    protected val server: Server = jettyServer().apply {
        initializeServer(environment)
    }

    override fun start(wait: Boolean) : JettyApplicationHostBase {
        environment.start()
        server.start()
        if (wait) {
            server.join()
            stop(1, 5, TimeUnit.SECONDS)
        }
        return this
    }

    override fun stop(gracePeriod: Long, timeout: Long, timeUnit: TimeUnit) {
        server.stopTimeout = timeUnit.toMillis(timeout)
        server.stop()
        server.destroy()
        environment.stop()
    }

    override fun toString(): String {
        return "Jetty($environment)"
    }
}
