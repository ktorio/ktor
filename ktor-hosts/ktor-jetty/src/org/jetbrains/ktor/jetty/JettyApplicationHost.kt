package org.jetbrains.ktor.jetty

import org.eclipse.jetty.server.*
import org.jetbrains.ktor.host.*

/**
 * [ApplicationHost] implementation for running standalone Jetty Host
 */
class JettyApplicationHost(environment: ApplicationHostEnvironment,
                           jettyServer: () -> Server = ::Server) : JettyApplicationHostBase(environment, jettyServer) {

    override fun start(wait: Boolean) : JettyApplicationHost {
        server.handler = JettyKtorHandler(environment, this::pipeline)
        super.start(wait)
        return this
    }
}
