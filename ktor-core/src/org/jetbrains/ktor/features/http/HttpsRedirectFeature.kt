package org.jetbrains.ktor.features.http

import org.jetbrains.ktor.application.*
import org.jetbrains.ktor.features.*
import org.jetbrains.ktor.response.*
import org.jetbrains.ktor.util.*

object HttpsRedirectFeature : ApplicationFeature<ApplicationCallPipeline, HttpsRedirectFeature.RedirectConfig> {
    override val key = AttributeKey<RedirectConfig>("https-redirect-feature")

    override fun install(pipeline: ApplicationCallPipeline, configure: RedirectConfig.() -> Unit): RedirectConfig {
        val config = RedirectConfig()
        config.configure()

        pipeline.intercept(ApplicationCallPipeline.Infrastructure) { call ->
            if (call.request.originRoute.scheme == "http") {
                call.respondRedirect(call.url { protocol = URLProtocol.HTTPS; port = config.sslPort }, permanent = config.permanentRedirect)
            }
        }

        return config
    }

    class RedirectConfig {
        var sslPort = URLProtocol.HTTPS.defaultPort
        var permanentRedirect = true
    }
}