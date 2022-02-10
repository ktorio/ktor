/*
 * Copyright 2014-2021 JetBrains s.r.o and contributors. Use of this source code is governed by the Apache 2.0 license.
 */

package io.ktor.server.engine

import io.ktor.http.*
import io.ktor.server.application.*
import io.ktor.server.request.*
import io.ktor.server.response.*
import io.ktor.util.*
import kotlinx.coroutines.*
import kotlin.system.*

/**
 * Shutdown URL plugin. It stops application when requested particular url
 *
 * @property url to handle
 * @property exitCode is a function to compute process exit code
 */
public class ShutDownUrl(public val url: String, public val exitCode: ApplicationCall.() -> Int) {
    /**
     * Does application shutdown using the specified [call]
     */
    public suspend fun doShutdown(call: ApplicationCall) {
        call.application.log.warn("Shutdown URL was called: server is going down")
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
     * A plugin to install into engine pipeline
     */
    public object EnginePlugin : ApplicationPlugin<EnginePipeline, Config, ShutDownUrl> {
        override val key: AttributeKey<ShutDownUrl> = AttributeKey<ShutDownUrl>("shutdown.url")

        override fun install(pipeline: EnginePipeline, configure: Config.() -> Unit): ShutDownUrl {
            val config = Config()
            configure(config)

            val plugin = ShutDownUrl(config.shutDownUrl, config.exitCodeSupplier)
            pipeline.intercept(EnginePipeline.Before) {
                if (call.request.uri == plugin.url) {
                    plugin.doShutdown(call)
                }
            }

            return plugin
        }
    }

    /**
     * A plugin to install into application call pipeline
     */
    public val ApplicationCallPlugin: ApplicationPlugin<Application, Config, PluginInstance> =
        createApplicationPlugin("shutdown.url", ::Config) {
            val plugin = ShutDownUrl(pluginConfig.shutDownUrl, pluginConfig.exitCodeSupplier)

            onCall { call ->
                if (call.request.uri == plugin.url) {
                    plugin.doShutdown(call)
                }
            }
        }

    /**
     * Shutdown url configuration builder
     */
    @KtorDsl
    public class Config {
        /**
         * URI to handle shutdown requests
         */
        public var shutDownUrl: String = "/ktor/application/shutdown"

        /**
         * A function that provides process exit code by an application call
         */
        public var exitCodeSupplier: ApplicationCall.() -> Int = { 0 }
    }
}
