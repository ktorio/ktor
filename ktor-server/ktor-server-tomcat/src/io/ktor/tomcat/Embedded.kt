package io.ktor.tomcat

import io.ktor.host.*

object Tomcat : ApplicationHostFactory<TomcatApplicationHost, TomcatApplicationHost.Configuration> {
    override fun create(environment: ApplicationHostEnvironment, configure: TomcatApplicationHost.Configuration.() -> Unit): TomcatApplicationHost {
        return TomcatApplicationHost(environment, configure)
    }
}
