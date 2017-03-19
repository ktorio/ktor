package org.jetbrains.ktor.tomcat

import org.jetbrains.ktor.application.*
import org.jetbrains.ktor.host.*

fun embeddedTomcatServer(port: Int = 80, host: String = "0.0.0.0", configure: Application.() -> Unit): TomcatApplicationHost {
    val hostConfig = applicationHostConfig {
        connector {
            this.port = port
            this.host = host
        }
    }

    val environment = applicationEnvironment {}
    return embeddedTomcatServer(hostConfig, environment, configure)
}

fun embeddedTomcatServer(port: Int = 80, host: String = "0.0.0.0", application: Application): TomcatApplicationHost {
    val hostConfig = applicationHostConfig {
        connector {
            this.port = port
            this.host = host
        }
    }

    val environment = applicationEnvironment {}
    return embeddedTomcatServer(hostConfig, environment, application)
}

fun embeddedTomcatServer(hostConfig: ApplicationHostConfig, environment: ApplicationEnvironment, configure: Application.() -> Unit): TomcatApplicationHost {
    return embeddedTomcatServer(hostConfig, environment, Application(environment, Unit).apply(configure))
}

fun embeddedTomcatServer(hostConfig: ApplicationHostConfig, environment: ApplicationEnvironment, application: Application): TomcatApplicationHost {
    environment.monitor.applicationStop += { environment.close() }
    return TomcatApplicationHost(hostConfig, environment, ApplicationLifecycleStatic(environment, application))
}



