package org.jetbrains.ktor.jetty

import org.eclipse.jetty.server.*
import org.jetbrains.ktor.host.*

/**
 * [ApplicationHost] implementation for running standalone Jetty Host
 */
class JettyApplicationHost(environment: ApplicationHostEnvironment,
                           jettyServer: () -> Server = ::Server) : JettyApplicationHostBase(environment, jettyServer) {

    init {
        server.handler = JettyKtorHandler(environment, server, this::pipeline)
    }

    override fun start(wait: Boolean) : JettyApplicationHost {
        super.start(wait)
        return this
    }
}
