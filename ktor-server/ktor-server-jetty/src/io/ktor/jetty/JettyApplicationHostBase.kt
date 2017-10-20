package io.ktor.jetty

import io.ktor.host.*
import org.eclipse.jetty.server.*
import java.util.concurrent.*

/**
 * [ApplicationHost] implementation for running standalone Jetty Host
 */
open class JettyApplicationHostBase(environment: ApplicationHostEnvironment,
                                    configure: Configuration.() -> Unit) : BaseApplicationHost(environment) {

    class Configuration : BaseApplicationHost.Configuration() {
        var configureServer: Server.() -> Unit = {}
    }

    private val configuration = Configuration().apply(configure)

    protected val server: Server = Server().apply {
        configuration.configureServer(this)
        initializeServer(environment)
    }

    override fun start(wait: Boolean): JettyApplicationHostBase {
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
