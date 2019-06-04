/*
 * Copyright 2014-2019 JetBrains s.r.o and contributors. Use of this source code is governed by the Apache 2.0 license.
 */

package io.ktor.server.netty

import io.ktor.config.*
import io.ktor.server.engine.*

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
        NettyApplicationEngine(applicationEnvironment, { loadConfiguration(applicationEnvironment.config) }).start()
    }

    private fun NettyApplicationEngine.Configuration.loadConfiguration(config: ApplicationConfig) {
        val deploymentConfig = config.config("ktor.deployment")
        loadCommonConfiguration(deploymentConfig)
        deploymentConfig.propertyOrNull("requestQueueLimit")?.getInt()?.let {
            requestQueueLimit = it
        }
        deploymentConfig.propertyOrNull("shareWorkGroup")?.getBoolean()?.let {
            shareWorkGroup = it
        }
        deploymentConfig.propertyOrNull("responseWriteTimeoutSeconds")?.getInt()?.let {
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
