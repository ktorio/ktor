package org.jetbrains.ktor.host

import org.jetbrains.ktor.application.*

fun defaultHostPipeline(environment: ApplicationEnvironment) = HostPipeline().apply {
    intercept(HostPipeline.Before, {
        onFinish {
            subject.close()
        }
    })

    environment.config.propertyOrNull("ktor.deployment.shutdown.url")?.getString()?.let { url ->
        install(ShutDownUrl.HostFeature) {
            shutDownUrl = url
        }
    }

    intercept(HostPipeline.Call) {
        fork(subject, subject.application)
    }

    setupDefaultHostPages(HostPipeline.Before, HostPipeline.Fallback)
}

