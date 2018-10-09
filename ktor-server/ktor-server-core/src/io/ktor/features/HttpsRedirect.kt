package io.ktor.features

import io.ktor.application.*
import io.ktor.http.*
import io.ktor.response.*
import io.ktor.util.*

/**
 * Redirect non-secure requests to HTTPS
 */
class HttpsRedirect(config: Configuration) {
    /**
     * HTTPS port to redirect to
     */
    val redirectPort: Int = config.sslPort

    /**
     * If it does permanent redirect
     */
    val permanent: Boolean = config.permanentRedirect

    /**
     * Redirect feature configuration
     */
    class Configuration {
        /**
         * HTTPS port (443 by default) to redirect to
         */
        var sslPort: Int = URLProtocol.HTTPS.defaultPort

        /**
         * Use permanent redirect or temporary
         */
        var permanentRedirect: Boolean = true
    }

    /**
     * Feature installation object
     */
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
