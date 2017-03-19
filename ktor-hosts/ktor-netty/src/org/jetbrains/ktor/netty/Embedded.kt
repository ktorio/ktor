package org.jetbrains.ktor.netty

import org.jetbrains.ktor.application.*
import org.jetbrains.ktor.host.*

fun embeddedNettyServer(port: Int = 80, host: String = "0.0.0.0", configure: Application.() -> Unit): NettyApplicationHost {
    val hostConfig = applicationHostConfig {
        connector {
            this.port = port
            this.host = host
        }
    }

    val environment = applicationEnvironment {}
    return embeddedNettyServer(hostConfig, environment, configure)
}

fun embeddedNettyServer(port: Int = 80, host: String = "0.0.0.0", application: Application): NettyApplicationHost {
    val hostConfig = applicationHostConfig {
        connector {
            this.port = port
            this.host = host
        }
    }

    val environment = applicationEnvironment {}
    return embeddedNettyServer(hostConfig, environment, application)
}

fun embeddedNettyServer(hostConfig: ApplicationHostConfig, environment: ApplicationEnvironment, configure: Application.() -> Unit): NettyApplicationHost {
    return embeddedNettyServer(hostConfig, environment, Application(environment, Unit).apply(configure))
}

fun embeddedNettyServer(hostConfig: ApplicationHostConfig, environment: ApplicationEnvironment, application: Application): NettyApplicationHost {
    environment.monitor.applicationStop += { environment.close() }
    return NettyApplicationHost(hostConfig, environment, ApplicationLifecycleStatic(environment, application))
}
