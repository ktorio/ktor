package io.ktor.server.host

import io.ktor.application.*
import io.ktor.http.*
import io.ktor.request.*
import io.ktor.response.*
import io.ktor.util.*
import kotlin.concurrent.*
import kotlin.system.*

class ShutDownUrl(val url: String, val exitCode: ApplicationCall.() -> Int) {

    suspend fun doShutdown(call: ApplicationCall) {
        call.application.log.warn("Shutdown URL was called: server is going down")
        thread {
            call.application.dispose()
            exitProcess(exitCode(call))
        }

        call.respond(HttpStatusCode.Gone)
    }

    object HostFeature : ApplicationFeature<HostPipeline, Configuration, ShutDownUrl> {
        override val key = AttributeKey<ShutDownUrl>("shutdown.url")

        override fun install(pipeline: HostPipeline, configure: Configuration.() -> Unit): ShutDownUrl {
            val config = Configuration()
            configure(config)

            val feature = ShutDownUrl(config.shutDownUrl, config.exitCodeSupplier)
            pipeline.intercept(HostPipeline.Before) {
                if (call.request.uri == feature.url) {
                    feature.doShutdown(call)
                }
            }

            return feature
        }
    }

    object ApplicationCallFeature : ApplicationFeature<ApplicationCallPipeline, Configuration, ShutDownUrl> {
        override val key = AttributeKey<ShutDownUrl>("shutdown.url")

        override fun install(pipeline: ApplicationCallPipeline, configure: Configuration.() -> Unit): ShutDownUrl {
            val config = Configuration()
            configure(config)

            val feature = ShutDownUrl(config.shutDownUrl, config.exitCodeSupplier)
            pipeline.intercept(ApplicationCallPipeline.Infrastructure) {
                if (call.request.uri == feature.url) {
                    feature.doShutdown(call)
                }
            }

            return feature
        }
    }

    class Configuration {
        var shutDownUrl = "/ktor/application/shutdown"
        var exitCodeSupplier: ApplicationCall.() -> Int = { 0 }
    }
}