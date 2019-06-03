/*
 * Copyright 2014-2019 JetBrains s.r.o and contributors. Use of this source code is governed by the Apache 2.0 license.
 */

package io.ktor.server.netty

import io.ktor.config.*
import io.ktor.server.engine.*
import java.util.concurrent.*

/**
 * Netty engine
 */
object EngineMain {
    /**
     * Main function for starting EngineMain with Netty
     * Creates an embedded Netty application with an environment built from command line arguments.
     */
    @JvmStatic
    fun main(args: Array<String>) {
        val applicationEnvironment = commandLineEnvironment(args)
        val engine = NettyApplicationEngine(applicationEnvironment) { loadConfiguration(applicationEnvironment.config) }
        engine.addShutdownHook {
            engine.stop(3, 5, TimeUnit.SECONDS)
        }
        engine.start(true)
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
