/*
 * Copyright 2014-2019 JetBrains s.r.o and contributors. Use of this source code is governed by the Apache 2.0 license.
 */

package io.ktor.server.engine

import io.ktor.application.*
import io.ktor.http.*
import io.ktor.request.*
import io.ktor.response.*
import io.ktor.util.*
import io.ktor.util.logging.*
import kotlinx.coroutines.*
import kotlin.system.*

/**
 * Shutdown URL feature. It stops application when requested particular url
 *
 * @property url to handle
 * @property exitCode is a function to compute process exit code
 */
class ShutDownUrl(val url: String, val exitCode: ApplicationCall.() -> Int) {
    /**
     * Does application shutdown using the specified [call]
     */
    suspend fun doShutdown(call: ApplicationCall) {
        call.application.log.warning("Shutdown URL was called: server is going down")
        val application = call.application
        val environment = application.environment
        val exitCode = exitCode(call)

        val latch = CompletableDeferred<Nothing>()
        call.application.launch {
            latch.join()

            environment.monitor.raise(ApplicationStopPreparing, environment)
            if (environment is ApplicationEngineEnvironment) {
                environment.stop()
            } else {
                application.dispose()
            }

            exitProcess(exitCode)
        }

        try {
            call.respond(HttpStatusCode.Gone)
        } finally {
            latch.cancel()
        }
    }

    /**
     * A feature to install into engine pipeline
     */
    object EngineFeature : ApplicationFeature<EnginePipeline, Configuration, ShutDownUrl> {
        override val key = AttributeKey<ShutDownUrl>("shutdown.url")

        override fun install(pipeline: EnginePipeline, configure: Configuration.() -> Unit): ShutDownUrl {
            val config = Configuration()
            configure(config)

            val feature = ShutDownUrl(config.shutDownUrl, config.exitCodeSupplier)
            pipeline.intercept(EnginePipeline.Before) {
                if (call.request.uri == feature.url) {
                    feature.doShutdown(call)
                }
            }

            return feature
        }
    }

    /**
     * A feature to install into application call pipeline
     */
    object ApplicationCallFeature : ApplicationFeature<ApplicationCallPipeline, Configuration, ShutDownUrl> {
        override val key: AttributeKey<ShutDownUrl> = AttributeKey("shutdown.url")

        override fun install(pipeline: ApplicationCallPipeline, configure: Configuration.() -> Unit): ShutDownUrl {
            val config = Configuration()
            configure(config)

            val feature = ShutDownUrl(config.shutDownUrl, config.exitCodeSupplier)
            pipeline.intercept(ApplicationCallPipeline.Features) {
                if (call.request.uri == feature.url) {
                    feature.doShutdown(call)
                }
            }

            return feature
        }
    }

    /**
     * Shutdown url configuration builder
     */
    class Configuration {
        /**
         * URI to handle shutdown requests
         */
        var shutDownUrl = "/ktor/application/shutdown"

        /**
         * A function that provides process exit code by an application call
         */
        var exitCodeSupplier: ApplicationCall.() -> Int = { 0 }
    }
}
