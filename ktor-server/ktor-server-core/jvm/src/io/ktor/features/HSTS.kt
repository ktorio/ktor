package io.ktor.features

import io.ktor.application.*
import io.ktor.http.*
import io.ktor.response.*
import io.ktor.util.*
import java.time.*
import java.util.*

/**
 * HSTS feature that appends `Strict-Transport-Security` HTTP header to every response.
 * See http://ktor.io/servers/features/hsts.html for details
 * See RFC 6797 https://tools.ietf.org/html/rfc6797
 */
class HSTS(config: Configuration) {
    /**
     * HSTS configuration
     */
    class Configuration {
        /**
         * Consents that the policy allows including the domain into web browser preloading list
         */
        var preload = false

        /**
         * Adds includeSubDomains directive, which applies this policy to this domain and any subdomains
         */
        var includeSubDomains = true

        /**
         * Duration to tell the client to keep the host in a list of known HSTS hosts
         */
        var maxAge: Duration = Duration.ofDays(365)

        /**
         * Any custom directives supported by specific user-agent
         */
        val customDirectives: MutableMap<String, String?> = HashMap()
    }

    /**
     * Constructed `Strict-Transport-Security` header value
     */
    val headerValue: String = buildString {
        append("max-age=")
        append(config.maxAge.toMillis() / 1000L)

        if (config.includeSubDomains) {
            append("; includeSubDomains")
        }
        if (config.preload) {
            append("; preload")
        }

        if (config.customDirectives.isNotEmpty()) {
            config.customDirectives.entries.joinTo(this, separator = "; ", prefix = "; ") {
                if (it.value != null) {
                    "${it.key.escapeIfNeeded()}=${it.value?.escapeIfNeeded()}"
                } else {
                    it.key.escapeIfNeeded()
                }
            }
        }
    }

    /**
     * Feature's main interceptor, usually installed by the feature itself
     */
    fun intercept(call: ApplicationCall) {
        if (call.request.origin.run { scheme == "https" && port == 443 }) {
            call.response.header(HttpHeaders.StrictTransportSecurity, headerValue)
        }
    }

    /**
     * Feature installation object
     */
    companion object Feature : ApplicationFeature<ApplicationCallPipeline, Configuration, HSTS> {
        override val key = AttributeKey<HSTS>("HSTS")
        override fun install(pipeline: ApplicationCallPipeline, configure: Configuration.() -> Unit): HSTS {
            val feature = HSTS(Configuration().apply(configure))
            pipeline.intercept(ApplicationCallPipeline.Features) { feature.intercept(call) }
            return feature
        }
    }
}
