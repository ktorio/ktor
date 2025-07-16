/*
 * Copyright 2014-2021 JetBrains s.r.o and contributors. Use of this source code is governed by the Apache 2.0 license.
 */

package io.ktor.server.engine

import io.ktor.http.*
import io.ktor.server.application.*
import io.ktor.server.request.*
import io.ktor.server.response.*
import io.ktor.util.*
import io.ktor.utils.io.*
import kotlinx.coroutines.*
import kotlin.system.*

/**
 * A plugin that allows you to configure a URL used to shut down the server. There are two ways to enable this plugin:
 * - In a HOCON configuration file.
 * - By installing the plugin.
 * You can learn more from [Shutdown URL](https://ktor.io/docs/shutdown-url.html).
 *
 *
 * [Report a problem](https://ktor.io/feedback/?fqname=io.ktor.server.engine.ShutDownUrl)
 *
 * @property url to handle
 * @property exitCode is a function to compute a process exit code
 * @property exit the function for exiting the process; defaults to the system function [exitProcess]
 */
public class ShutDownUrl(
    public val url: String,
    public val exitCode: ApplicationCall.() -> Int,
    public val exit: (Int) -> Unit = ::exitProcess
) {
    /**
     * Shuts down an application using the specified [call].
     *
     * [Report a problem](https://ktor.io/feedback/?fqname=io.ktor.server.engine.ShutDownUrl.doShutdown)
     */
    @OptIn(InternalAPI::class)
    public suspend fun doShutdown(call: ApplicationCall) {
        val application = call.application
        val log = application.log
        val environment = application.environment
        log.warn("Shutdown URL was called: server is going down")

        val latch = CompletableDeferred<Nothing>()

        // Launch in an independent scope,
        // so it may outlive the application for process shutdown
        CoroutineScope(Dispatchers.Default).launch {
            try {
                latch.join()

                application.monitor.raise(ApplicationStopPreparing, environment)
                application.disposeAndJoin()

                exit(this@ShutDownUrl.exitCode(call))
            } catch (e: Exception) {
                log.error("Exception occurred during shutdown!", e)
                exit(1)
            }
        }

        try {
            call.respond(HttpStatusCode.Gone)
        } finally {
            latch.cancel()
        }
    }

    /**
     * A plugin to install into an engine pipeline.
     *
     * [Report a problem](https://ktor.io/feedback/?fqname=io.ktor.server.engine.ShutDownUrl.EnginePlugin)
     */
    public object EnginePlugin : BaseApplicationPlugin<EnginePipeline, Config, ShutDownUrl> {
        override val key: AttributeKey<ShutDownUrl> = AttributeKey("shutdown.url")

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
     * A configuration for the [ShutDownUrl] plugin.
     *
     * [Report a problem](https://ktor.io/feedback/?fqname=io.ktor.server.engine.ShutDownUrl.Config)
     */
    @KtorDsl
    public class Config {
        /**
         * Specifies a URI used to handle a shutdown request.
         *
         * [Report a problem](https://ktor.io/feedback/?fqname=io.ktor.server.engine.ShutDownUrl.Config.shutDownUrl)
         */
        public var shutDownUrl: String = "/ktor/application/shutdown"

        /**
         * A function that provides a process exit code by an application call.
         *
         * [Report a problem](https://ktor.io/feedback/?fqname=io.ktor.server.engine.ShutDownUrl.Config.exitCodeSupplier)
         */
        public var exitCodeSupplier: ApplicationCall.() -> Int = { 0 }

        /**
         * Internal config item for testing shutdown.
         */
        public var exit: (Int) -> Unit = ::exitProcess
    }

    public companion object {

        /**
         * An installation object of the [ShutDownUrl] plugin.
         *
         * [Report a problem](https://ktor.io/feedback/?fqname=io.ktor.server.engine.ShutDownUrl.Companion.ApplicationCallPlugin)
         */
        public val ApplicationCallPlugin: BaseApplicationPlugin<Application, Config, PluginInstance> =
            createApplicationPlugin("shutdown.url", ::Config) {
                val plugin = ShutDownUrl(
                    pluginConfig.shutDownUrl,
                    pluginConfig.exitCodeSupplier,
                    pluginConfig.exit
                )

                onCall { call ->
                    if (call.request.uri == plugin.url) {
                        plugin.doShutdown(call)
                    }
                }
            }
    }
}
