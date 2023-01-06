/*
 * Copyright 2014-2019 JetBrains s.r.o and contributors. Use of this source code is governed by the Apache 2.0 license.
 */

package io.ktor.server.tomcat

import io.ktor.server.config.*
import io.ktor.server.engine.*
import java.util.concurrent.*

/**
 * Tomcat engine
 */
public object EngineMain {
    /**
     * Main function for starting EngineMain with Tomcat
     * Creates an embedded Tomcat application with an environment built from command line arguments.
     */
    @JvmStatic
    public fun main(args: Array<String>) {
        val applicationEnvironment = commandLineEnvironment(args)
        val engine = TomcatApplicationEngine(applicationEnvironment) {
            loadConfiguration(applicationEnvironment.config)
        }
        engine.addShutdownHook {
            engine.stop(3, 5, TimeUnit.SECONDS)
        }
        engine.start(true)
    }

    private fun TomcatApplicationEngine.Configuration.loadConfiguration(config: ApplicationConfig) {
        val deploymentConfig = config.config("ktor.deployment")
        loadCommonConfiguration(deploymentConfig)
    }
}
