package io.ktor.features

import io.ktor.application.*
import io.ktor.http.*
import io.ktor.response.*
import io.ktor.util.*

class HttpsRedirect(config: Configuration) {
    val redirectPort = config.sslPort
    val permanent = config.permanentRedirect

    class Configuration {
        var sslPort = URLProtocol.HTTPS.defaultPort
        var permanentRedirect = true
    }

    companion object Feature : ApplicationFeature<ApplicationCallPipeline, Configuration, HttpsRedirect> {
        override val key = AttributeKey<HttpsRedirect>("HttpsRedirect")
        override fun install(pipeline: ApplicationCallPipeline, configure: Configuration.() -> Unit): HttpsRedirect {
            val feature = HttpsRedirect(Configuration().apply(configure))
            pipeline.intercept(ApplicationCallPipeline.Features) {
                if (call.request.origin.scheme == "http") {
                    val redirectUrl = call.url { protocol = URLProtocol.HTTPS; port = feature.redirectPort }
                    call.respondRedirect(redirectUrl, feature.permanent)
                    finish()
                }
            }
            return feature
        }
    }
}