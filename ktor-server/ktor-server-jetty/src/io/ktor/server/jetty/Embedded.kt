package io.ktor.server.jetty

import io.ktor.server.host.*

object Jetty : ApplicationHostFactory<JettyApplicationHost, JettyApplicationHostBase.Configuration> {
    override fun create(environment: ApplicationHostEnvironment, configure: JettyApplicationHostBase.Configuration.() -> Unit): JettyApplicationHost {
        return JettyApplicationHost(environment, configure)
    }
}
