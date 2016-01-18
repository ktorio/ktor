package org.jetbrains.ktor.jetty

import org.jetbrains.ktor.application.*
import org.jetbrains.ktor.host.*
import org.jetbrains.ktor.routing.*

fun embeddedJettyServer(port: Int, application: Routing.() -> Unit) = embeddedJettyServer(applicationConfig { this.port = port }, application)
fun embeddedJettyServer(config: ApplicationConfig, application: Routing.() -> Unit) : ApplicationHost {
    val applicationObject = object : Application(config) {
        init {
            Routing().apply(application).installInto(this)
        }
    }
    return JettyApplicationHost(config, applicationObject)
}

