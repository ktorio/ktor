package io.ktor.server.cio

import io.ktor.config.*
import io.ktor.server.engine.*
import java.util.concurrent.*

/**
 * Default development engine with main function that starts CIO engine using application.conf
 */
object EngineMain {
    /**
     * CIO development engine entry point
     */
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

@Suppress("KDocMissingDocumentation")
@Deprecated(
    "Use EngineMain instead",
    replaceWith = ReplaceWith("EngineMain"),
    level = DeprecationLevel.HIDDEN
)
object DevelopmentEngine {
    @JvmStatic
    fun main(args: Array<String>): Unit = EngineMain.main(args)
}
