package org.jetbrains.ktor.features

import org.jetbrains.ktor.application.*
import org.jetbrains.ktor.http.*
import org.jetbrains.ktor.response.*
import org.jetbrains.ktor.util.*
import java.time.*
import java.util.*

class HSTS(config: Configuration) {
    class Configuration {
        var preload = false
        var includeSubDomains = true
        var maxAge = Duration.ofDays(365)

        val customDirectives: MutableMap<String, String?> = HashMap()
    }

    // see RFC 6797 https://tools.ietf.org/html/rfc6797
    val headerValue = buildString {
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

    fun intercept(call: ApplicationCall) {
        if (call.request.origin.run { scheme == "https" && port == 443 }) {
            call.response.header(HttpHeaders.StrictTransportSecurity, headerValue)
        }
    }

    companion object Feature : ApplicationFeature<ApplicationCallPipeline, Configuration, HSTS> {
        override val key = AttributeKey<HSTS>("HSTS")
        override fun install(pipeline: ApplicationCallPipeline, configure: Configuration.() -> Unit): HSTS {
            val feature = HSTS(Configuration().apply(configure))
            pipeline.intercept(ApplicationCallPipeline.Infrastructure) { feature.intercept(call) }
            return feature
        }
    }
}