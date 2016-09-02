package org.jetbrains.ktor.netty

import org.jetbrains.ktor.application.*
import org.jetbrains.ktor.host.*
import org.jetbrains.ktor.routing.*

fun embeddedNettyServer(port: Int = 80, host: String = "0.0.0.0", routing: Routing.() -> Unit): NettyApplicationHost {
    val hostConfig = applicationHostConfig {
        connector {
            this.port = port
            this.host = host
        }
    }

    val applicationConfig = applicationEnvironment {}
    return embeddedNettyServer(hostConfig, applicationConfig, routing)
}

fun embeddedNettyServer(port: Int = 80, host: String = "0.0.0.0", application: Application): NettyApplicationHost {
    val hostConfig = applicationHostConfig {
        connector {
            this.port = port
            this.host = host
        }
    }

    val applicationConfig = applicationEnvironment {}
    return embeddedNettyServer(hostConfig, applicationConfig, application)
}

fun embeddedNettyServer(hostConfig: ApplicationHostConfig, environment: ApplicationEnvironment, application: Application): NettyApplicationHost {
    return NettyApplicationHost(hostConfig, environment, object : ApplicationLifecycle {
        override val application: Application = application
        override fun onBeforeInitializeApplication(initializer: Application.() -> Unit) {
            application.initializer()
        }

        override fun dispose() = application.dispose()
    })
}

fun embeddedNettyServer(hostConfig: ApplicationHostConfig, environment: ApplicationEnvironment, routing: Routing.() -> Unit): NettyApplicationHost {
    return embeddedNettyServer(hostConfig, environment, Application(environment, Unit).apply {
        routing(routing)
    })
}
