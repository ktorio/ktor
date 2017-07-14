package org.jetbrains.ktor.host

import org.jetbrains.ktor.application.*
import org.slf4j.*

interface ApplicationHostFactory<out THost : ApplicationHost> {
    fun create(environment: ApplicationHostEnvironment): THost
}

fun <THost : ApplicationHost> embeddedServer(factory: ApplicationHostFactory<THost>,
                                             port: Int = 80,
                                             host: String = "0.0.0.0",
                                             watchPaths: List<String> = emptyList(),
                                             module: Application.() -> Unit): THost {
    val environment = applicationHostEnvironment {
        this.log = LoggerFactory.getLogger("ktor.application")
        this.watchPaths = watchPaths
        this.module(module)

        connector {
            this.port = port
            this.host = host
        }
    }

    return embeddedServer(factory, environment)
}

fun <THost : ApplicationHost> embeddedServer(factory: ApplicationHostFactory<THost>, environment: ApplicationHostEnvironment): THost {
    return factory.create(environment)
}

