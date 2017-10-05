package io.ktor.jetty

import io.ktor.host.*

object Jetty : ApplicationHostFactory<JettyApplicationHost> {
    override fun create(environment: ApplicationHostEnvironment) = JettyApplicationHost(environment)
}
