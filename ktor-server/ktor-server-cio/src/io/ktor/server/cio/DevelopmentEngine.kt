package io.ktor.server.cio

import io.ktor.config.*
import io.ktor.server.engine.*
import java.util.concurrent.*

object DevelopmentEngine {
    @JvmStatic
    fun main(args: Array<String>) {
        val applicationEnvironment = commandLineEnvironment(args)
        val engine = CIOApplicationEngine(applicationEnvironment, { loadConfiguration(applicationEnvironment.config) })
        Runtime.getRuntime().addShutdownHook(object : Thread() {
            override fun run() {
                engine.stop(3, 5, TimeUnit.SECONDS)
            }
        })
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
