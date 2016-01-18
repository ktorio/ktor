package org.jetbrains.ktor.netty

import org.jetbrains.ktor.application.*
import org.jetbrains.ktor.host.*
import org.jetbrains.ktor.routing.*

fun embeddedNettyServer(port: Int, application: Routing.() -> Unit) = embeddedNettyServer(applicationConfig { this.port = port }, application)
fun embeddedNettyServer(config: ApplicationConfig, application: Routing.() -> Unit): ApplicationHost {
    val applicationObject = object : Application(config) {
        init {
            Routing().apply(application).installInto(this)
        }
    }
    return NettyApplicationHost(config, applicationObject)
}

