package io.ktor.server.jetty

import io.ktor.config.*
import io.ktor.server.engine.*

/**
 * Jetty development engine
 */
object DevelopmentEngine {
    /**
     * Main function for starting DevelopmentEngine with Jetty
     * Creates an embedded Jetty application with an environment built from command line arguments.
     */
    @JvmStatic
    fun main(args: Array<String>) {
        val applicationEnvironment = commandLineEnvironment(args)
        JettyApplicationEngine(applicationEnvironment, { loadConfiguration(applicationEnvironment.config) }).start()
    }

    private fun JettyApplicationEngineBase.Configuration.loadConfiguration(config: ApplicationConfig) {
        val deploymentConfig = config.config("ktor.deployment")
        loadCommonConfiguration(deploymentConfig)
    }
}
