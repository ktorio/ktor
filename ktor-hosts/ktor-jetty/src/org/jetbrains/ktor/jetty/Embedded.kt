package org.jetbrains.ktor.jetty

import org.jetbrains.ktor.application.*
import org.jetbrains.ktor.host.*
import org.jetbrains.ktor.routing.*

fun embeddedJettyServer(port: Int = 80, host: String = "0.0.0.0", routing: Routing.() -> Unit): JettyApplicationHost {
    val hostConfig = applicationHostConfig {
        connector {
            this.port = port
            this.host = host
        }
    }

    val applicationConfig = applicationEnvironment {}
    return embeddedJettyServer(hostConfig, applicationConfig, routing)
}

fun embeddedJettyServer(port: Int = 80, host: String = "0.0.0.0", application: Application): ApplicationHost {
    val hostConfig = applicationHostConfig {
        connector {
            this.port = port
            this.host = host
        }
    }

    val applicationConfig = applicationEnvironment {}
    return embeddedJettyServer(hostConfig, applicationConfig, application)
}

fun embeddedJettyServer(hostConfig: ApplicationHostConfig, environment: ApplicationEnvironment, application: Application): JettyApplicationHost {
    return JettyApplicationHost(hostConfig, environment, object : ApplicationLifecycle {
        override val application: Application = application
        override fun onBeforeInitializeApplication(initializer: Application.() -> Unit) {
            application.initializer()
        }

        override fun dispose() = application.dispose()
    })
}

fun embeddedJettyServer(hostConfig: ApplicationHostConfig, environment: ApplicationEnvironment, routing: Routing.() -> Unit): JettyApplicationHost {
    return embeddedJettyServer(hostConfig, environment, Application(environment, Unit).apply {
        routing(routing)
    })
}

