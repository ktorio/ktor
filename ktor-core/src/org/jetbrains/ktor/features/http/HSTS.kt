package org.jetbrains.ktor.features.http

import org.jetbrains.ktor.application.*
import org.jetbrains.ktor.features.*
import org.jetbrains.ktor.http.*
import org.jetbrains.ktor.response.*
import org.jetbrains.ktor.util.*
import java.time.*
import java.util.*

// see RFC 6797 https://tools.ietf.org/html/rfc6797
object HSTS : ApplicationFeature<ApplicationCallPipeline, HSTS.HSTSConfig> {
    override val key = AttributeKey<HSTSConfig>("HSTS")

    override fun install(pipeline: ApplicationCallPipeline, configure: HSTSConfig.() -> Unit): HSTSConfig {
        val config = HSTSConfig()
        config.configure()

        val hstsHeaderValue = buildString {
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

        pipeline.intercept(ApplicationCallPipeline.Infrastructure) { call ->
            if (call.request.originRoute.run { scheme == "https" && port == 443 }) {
                call.response.header(HttpHeaders.StrictTransportSecurity, hstsHeaderValue)
            }
        }

        return config
    }

    class HSTSConfig {
        var preload = false
        var includeSubDomains = true
        var maxAge = Duration.ofDays(365)

        val customDirectives: MutableMap<String, String?> = HashMap()
    }
}