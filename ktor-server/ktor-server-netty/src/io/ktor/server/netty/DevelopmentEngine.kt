package io.ktor.server.netty

import io.ktor.config.*
import io.ktor.server.engine.*

/**
 * Netty development engine
 */
object DevelopmentEngine {
    /**
     * Main function for starting DevelopmentEngine with Netty
     * Creates an embedded Netty application with an environment built from command line arguments.
     */
    @JvmStatic
    fun main(args: Array<String>) {
        val applicationEnvironment = commandLineEnvironment(args)
        NettyApplicationEngine(applicationEnvironment, { loadConfiguration(applicationEnvironment.config) }).start()
    }

    private fun NettyApplicationEngine.Configuration.loadConfiguration(config: ApplicationConfig) {
        val deploymentConfig = config.config("ktor.deployment")
        loadCommonConfiguration(deploymentConfig)
        deploymentConfig.propertyOrNull("requestQueueLimit")?.getString()?.toInt()?.let {
            requestQueueLimit = it
        }
        deploymentConfig.propertyOrNull("shareWorkGroup")?.getString()?.toBoolean()?.let {
            shareWorkGroup = it
        }
        deploymentConfig.propertyOrNull("responseWriteTimeoutSeconds")?.getString()?.toInt()?.let {
            responseWriteTimeoutSeconds = it
        }
    }
}
