package org.jetbrains.ktor.host

import org.jetbrains.ktor.application.*

fun defaultHostPipeline(environment: ApplicationEnvironment) = HostPipeline().apply {
    intercept(HostPipeline.Before) {
        subject.use { proceed() }
    }

    environment.config.propertyOrNull("ktor.deployment.shutdown.url")?.getString()?.let { url ->
        install(ShutDownUrl.HostFeature) {
            shutDownUrl = url
        }
    }

    intercept(HostPipeline.Call) {
        subject.application.execute(subject)
        proceed()
    }

    setupDefaultHostPages(HostPipeline.Before, HostPipeline.Fallback)
}

