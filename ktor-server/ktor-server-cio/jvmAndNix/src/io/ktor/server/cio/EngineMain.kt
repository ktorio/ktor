/*
 * Copyright 2014-2019 JetBrains s.r.o and contributors. Use of this source code is governed by the Apache 2.0 license.
 */

package io.ktor.server.cio

import io.ktor.server.config.*
import io.ktor.server.engine.*
import kotlin.jvm.*

/**
 * Default engine with main function that starts CIO engine using application.conf
 */
public object EngineMain {
    /**
     * CIO engine entry point
     */
    @JvmStatic
    public fun main(args: Array<String>) {
        val applicationEnvironment = commandLineEnvironment(args)
        val engine = CIOApplicationEngine(applicationEnvironment) { loadConfiguration(applicationEnvironment.config) }
        val gracePeriod =
            engine.environment.config.propertyOrNull("ktor.deployment.shutdownGracePeriod")?.getString()?.toLong()
                ?: 50
        val timeout =
            engine.environment.config.propertyOrNull("ktor.deployment.shutdownTimeout")?.getString()?.toLong()
                ?: 5000
        engine.addShutdownHook {
            engine.stop(gracePeriod, timeout)
        }
        engine.start(true)
    }

    private fun CIOApplicationEngine.Configuration.loadConfiguration(config: ApplicationConfig) {
        val deploymentConfig = config.config("ktor.deployment")
        loadCommonConfiguration(deploymentConfig)
        deploymentConfig.propertyOrNull("connectionIdleTimeoutSeconds")?.getString()?.toInt()?.let {
            connectionIdleTimeoutSeconds = it
        }
    }
}
