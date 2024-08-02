/*
 * Copyright 2014-2019 JetBrains s.r.o and contributors. Use of this source code is governed by the Apache 2.0 license.
 */

package io.ktor.server.tomcat

import io.ktor.server.config.*
import io.ktor.server.engine.*

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
        val config = CommandLineConfig(args)
        val server = EmbeddedServer(config.serverParameters, Tomcat) {
            takeFrom(config.engineConfig)
            loadConfiguration(config.serverParameters.environment.config)
        }
        server.start(true)
    }

    private fun TomcatServerEngine.Configuration.loadConfiguration(config: ServerConfig) {
        val deploymentConfig = config.config("ktor.deployment")
        loadCommonConfiguration(deploymentConfig)
    }
}
