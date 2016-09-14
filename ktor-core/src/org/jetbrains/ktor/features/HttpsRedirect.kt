package org.jetbrains.ktor.features

import org.jetbrains.ktor.application.*
import org.jetbrains.ktor.response.*
import org.jetbrains.ktor.util.*

class HttpsRedirect(config: Configuration) {
    val redirectPort = config.sslPort
    val permanent = config.permanentRedirect

    class Configuration {
        var sslPort = URLProtocol.HTTPS.defaultPort
        var permanentRedirect = true
    }

    fun intercept(call: ApplicationCall) {
        if (call.request.origin.scheme == "http") {
            val redirectUrl = call.url { protocol = URLProtocol.HTTPS; port = redirectPort }
            call.respondRedirect(redirectUrl, permanent)
        }
    }

    companion object Feature : ApplicationFeature<ApplicationCallPipeline, Configuration, HttpsRedirect> {
        override val key = AttributeKey<HttpsRedirect>("HttpsRedirect")
        override fun install(pipeline: ApplicationCallPipeline, configure: Configuration.() -> Unit): HttpsRedirect {
            val feature = HttpsRedirect(Configuration().apply(configure))
            pipeline.intercept(ApplicationCallPipeline.Infrastructure) { feature.intercept(call) }
            return feature
        }
    }
}