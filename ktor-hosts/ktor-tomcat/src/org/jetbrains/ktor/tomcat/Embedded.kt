package org.jetbrains.ktor.tomcat

import org.jetbrains.ktor.application.*
import org.jetbrains.ktor.config.*
import org.jetbrains.ktor.host.*
import org.jetbrains.ktor.routing.*

fun embeddedTomcatServer(port: Int = 80, host: String = "0.0.0.0", application: Routing.() -> Unit): ApplicationHost {
    val hostConfig = applicationHostConfig {
        connector {
            this.port = port
            this.host = host
        }
    }
    val applicationConfig = applicationEnvironment {}
    return embeddedTomcatServer(hostConfig, applicationConfig, application)
}

fun embeddedTomcatServer(hostConfig: ApplicationHostConfig, environment: ApplicationEnvironment, application: Routing.() -> Unit): ApplicationHost {
    val applicationObject = object : Application(environment) {
        init {
            routing(application)
        }
    }
    return TomcatApplicationHost(hostConfig, environment, object : ApplicationLifecycle {
        override val application: Application = applicationObject
        override fun dispose() {}
    })
}


