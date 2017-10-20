package io.ktor.host

import io.ktor.application.*
import org.slf4j.*

interface ApplicationHostFactory<out THost : ApplicationHost, TConfiguration : ApplicationHost.Configuration> {
    fun create(environment: ApplicationHostEnvironment, configure: TConfiguration.() -> Unit): THost
}

fun <THost : ApplicationHost, TConfiguration : ApplicationHost.Configuration>
        embeddedServer(factory: ApplicationHostFactory<THost, TConfiguration>,
                       port: Int = 80,
                       host: String = "0.0.0.0",
                       watchPaths: List<String> = emptyList(),
                       configure: TConfiguration.() -> Unit = {},
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

    return embeddedServer(factory, environment, configure)
}

fun <THost : ApplicationHost, TConfiguration : ApplicationHost.Configuration> embeddedServer(factory: ApplicationHostFactory<THost, TConfiguration>, environment: ApplicationHostEnvironment, configure: TConfiguration.() -> Unit = {}): THost {
    return factory.create(environment, configure)
}

