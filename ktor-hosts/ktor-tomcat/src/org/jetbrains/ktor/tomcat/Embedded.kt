package org.jetbrains.ktor.tomcat

import org.jetbrains.ktor.host.*

object Tomcat : ApplicationHostFactory<TomcatApplicationHost> {
    override fun create(environment: ApplicationHostEnvironment) = TomcatApplicationHost(environment)
}
