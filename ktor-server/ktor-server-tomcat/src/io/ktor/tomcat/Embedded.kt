package io.ktor.tomcat

import io.ktor.host.*

object Tomcat : ApplicationHostFactory<TomcatApplicationHost> {
    override fun create(environment: ApplicationHostEnvironment) = TomcatApplicationHost(environment)
}
