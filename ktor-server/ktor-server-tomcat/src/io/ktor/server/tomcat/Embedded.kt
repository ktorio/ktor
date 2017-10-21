package io.ktor.server.tomcat

import io.ktor.server.host.*

object Tomcat : ApplicationHostFactory<TomcatApplicationHost, TomcatApplicationHost.Configuration> {
    override fun create(environment: ApplicationHostEnvironment, configure: TomcatApplicationHost.Configuration.() -> Unit): TomcatApplicationHost {
        return TomcatApplicationHost(environment, configure)
    }
}
