/*
 * Copyright 2014-2019 JetBrains s.r.o and contributors. Use of this source code is governed by the Apache 2.0 license.
 */

package io.ktor.server.netty

import io.ktor.server.config.*
import io.ktor.server.engine.*
import java.util.concurrent.*
import kotlin.time.Duration.Companion.seconds

/**
 * Netty engine
 */
public object EngineMain {
    /**
     * Main function for starting EngineMain with Netty
     * Creates an embedded Netty application with an environment built from command line arguments.
     */
    @JvmStatic
    public fun main(args: Array<String>) {
        val applicationEnvironment = commandLineEnvironment(args)
        val engine = NettyApplicationEngine(applicationEnvironment) { loadConfiguration(applicationEnvironment.config) }
        engine.addShutdownHook {
            engine.stop(3.seconds, 5.seconds)
        }
        engine.start(true)
    }

    private fun NettyApplicationEngine.Configuration.loadConfiguration(config: ApplicationConfig) {
        val deploymentConfig = config.config("ktor.deployment")
        loadCommonConfiguration(deploymentConfig)
        deploymentConfig.propertyOrNull("requestQueueLimit")?.getString()?.toInt()?.let {
            requestQueueLimit = it
        }
        deploymentConfig.propertyOrNull("runningLimit")?.getString()?.toInt()?.let {
            runningLimit = it
        }
        deploymentConfig.propertyOrNull("shareWorkGroup")?.getString()?.toBoolean()?.let {
            shareWorkGroup = it
        }
        deploymentConfig.propertyOrNull("responseWriteTimeoutSeconds")?.getString()?.toInt()?.let {
            responseWriteTimeout = it.seconds
        }
        deploymentConfig.propertyOrNull("requestReadTimeoutSeconds")?.getString()?.toInt()?.let {
            requestReadTimeout = it.seconds
        }
        deploymentConfig.propertyOrNull("tcpKeepAlive")?.getString()?.toBoolean()?.let {
            tcpKeepAlive = it
        }
    }
}
