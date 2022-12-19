/*
 * Copyright 2014-2019 JetBrains s.r.o and contributors. Use of this source code is governed by the Apache 2.0 license.
 */

package io.ktor.server.jetty

import io.ktor.server.config.*
import io.ktor.server.engine.*

/**
 * Jetty engine
 */
public object EngineMain {
    /**
     * Main function for starting EngineMain with Jetty
     * Creates an embedded Jetty application with an environment built from command line arguments.
     */
    @JvmStatic
    public fun main(args: Array<String>) {
        val applicationEnvironment = commandLineEnvironment(args)
        val engine = JettyApplicationEngine(applicationEnvironment) { loadConfiguration(applicationEnvironment.config) }
        val gracePeriod =
            engine.environment.config.propertyOrNull("ktor.deployment.gracePeriod")?.getString()?.toLong() ?: 3000
        val timeout =
            engine.environment.config.propertyOrNull("ktor.deployment.timeout")?.getString()?.toLong() ?: 5000
        engine.addShutdownHook {
            engine.stop(gracePeriod, timeout)
        }
        engine.start(true)
    }

    private fun JettyApplicationEngineBase.Configuration.loadConfiguration(config: ApplicationConfig) {
        val deploymentConfig = config.config("ktor.deployment")
        loadCommonConfiguration(deploymentConfig)
    }
}
