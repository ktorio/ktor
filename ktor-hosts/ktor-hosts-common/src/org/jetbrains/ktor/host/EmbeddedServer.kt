package org.jetbrains.ktor.host

import org.jetbrains.ktor.application.*

interface ApplicationHostFactory<out THost : ApplicationHost> {
    fun create(environment: ApplicationHostEnvironment): THost
}

fun <THost : ApplicationHost> embeddedServer(factory: ApplicationHostFactory<THost>, port: Int = 80, host: String = "0.0.0.0", main: Application.() -> Unit): THost {
    val environment = applicationHostEnvironment {
        connector {
            this.port = port
            this.host = host
        }
        module(main)
    }

    return embeddedServer(factory, environment)
}

fun <THost : ApplicationHost> embeddedServer(factory: ApplicationHostFactory<THost>, environment: ApplicationHostEnvironment): THost {
    return factory.create(environment)
}

