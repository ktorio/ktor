package io.ktor.server.engine

import io.ktor.application.*
import io.ktor.http.*
import io.ktor.request.*
import io.ktor.response.*
import io.ktor.util.*
import java.util.concurrent.*
import kotlin.concurrent.*
import kotlin.system.*

class ShutDownUrl(val url: String, val exitCode: ApplicationCall.() -> Int) {
    suspend fun doShutdown(call: ApplicationCall) {
        call.application.log.warn("Shutdown URL was called: server is going down")
        val application = call.application
        val environment = application.environment
        val exitCode = exitCode(call)

        val latch = CountDownLatch(1)
        thread {
            latch.await(10L, TimeUnit.SECONDS)

            environment.monitor.raise(ApplicationStopPreparing, environment)
            if (environment is ApplicationEngineEnvironment) {
                environment.stop()
            } else {
                application.dispose()
            }

            exitProcess(exitCode)
        }

        call.respond(HttpStatusCode.Gone)
        latch.countDown()
    }

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

    object ApplicationCallFeature : ApplicationFeature<ApplicationCallPipeline, Configuration, ShutDownUrl> {
        override val key = AttributeKey<ShutDownUrl>("shutdown.url")

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

    class Configuration {
        var shutDownUrl = "/ktor/application/shutdown"
        var exitCodeSupplier: ApplicationCall.() -> Int = { 0 }
    }
}