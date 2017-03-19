package org.jetbrains.ktor.jetty

import org.jetbrains.ktor.application.*
import org.jetbrains.ktor.host.*

fun embeddedJettyServer(port: Int = 80, host: String = "0.0.0.0", configure: Application.() -> Unit): JettyApplicationHost {
    val hostConfig = applicationHostConfig {
        connector {
            this.port = port
            this.host = host
        }
    }

    val environment = applicationEnvironment {}
    return embeddedJettyServer(hostConfig, environment, configure)
}

fun embeddedJettyServer(port: Int = 80, host: String = "0.0.0.0", application: Application): JettyApplicationHost {
    val hostConfig = applicationHostConfig {
        connector {
            this.port = port
            this.host = host
        }
    }

    val environment = applicationEnvironment {}
    return embeddedJettyServer(hostConfig, environment, application)
}

fun embeddedJettyServer(hostConfig: ApplicationHostConfig, environment: ApplicationEnvironment, configure: Application.() -> Unit): JettyApplicationHost {
    return embeddedJettyServer(hostConfig, environment, Application(environment, Unit).apply(configure))
}

fun embeddedJettyServer(hostConfig: ApplicationHostConfig, environment: ApplicationEnvironment, application: Application): JettyApplicationHost {
    environment.monitor.applicationStop += { environment.close() }
    return JettyApplicationHost(hostConfig, environment, ApplicationLifecycleStatic(environment, application))
}
