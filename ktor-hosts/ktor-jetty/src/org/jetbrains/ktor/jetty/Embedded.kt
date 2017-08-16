package org.jetbrains.ktor.jetty

import org.jetbrains.ktor.host.*

object Jetty : ApplicationHostFactory<JettyApplicationHost> {
    override fun create(environment: ApplicationHostEnvironment) = JettyApplicationHost(environment)
}
