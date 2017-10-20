package io.ktor.jetty

import io.ktor.host.*

object Jetty : ApplicationHostFactory<JettyApplicationHost, JettyApplicationHostBase.Configuration> {
    override fun create(environment: ApplicationHostEnvironment, configure: JettyApplicationHostBase.Configuration.() -> Unit): JettyApplicationHost {
        return JettyApplicationHost(environment, configure)
    }
}
