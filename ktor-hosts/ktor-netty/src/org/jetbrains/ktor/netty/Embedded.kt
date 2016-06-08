package org.jetbrains.ktor.netty

import org.jetbrains.ktor.application.*
import org.jetbrains.ktor.host.*
import org.jetbrains.ktor.routing.*

fun embeddedNettyServer(port: Int = 80, host: String = "0.0.0.0", application: Routing.() -> Unit): ApplicationHost {
    val hostConfig = applicationHostConfig {
        this.port = port
        this.host = host
    }
    val applicationConfig = applicationEnvironment {
        stage = ApplicationEnvironmentStage.Embedded
    }
    return embeddedNettyServer(hostConfig, applicationConfig, application)
}

fun embeddedNettyServer(hostConfig: ApplicationHostConfig, environment: ApplicationEnvironment, application: Routing.() -> Unit): ApplicationHost {
    val applicationObject = object : Application(environment) {
        init {
            routing(application)
        }
    }
    return NettyApplicationHost(hostConfig, environment, applicationObject)
}
