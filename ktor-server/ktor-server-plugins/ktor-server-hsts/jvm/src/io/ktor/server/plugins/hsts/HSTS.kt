/*
 * Copyright 2014-2022 JetBrains s.r.o and contributors. Use of this source code is governed by the Apache 2.0 license.
 */

package io.ktor.server.plugins.hsts

import io.ktor.http.*
import io.ktor.server.application.*
import io.ktor.server.plugins.*
import io.ktor.server.response.*
import io.ktor.util.*

/**
 * HSTS plugin that appends `Strict-Transport-Security` HTTP header to every response.
 * See http://ktor.io/servers/features/hsts.html for details
 * See RFC 6797 https://tools.ietf.org/html/rfc6797
 */
public class HSTS private constructor(config: Configuration) {
    /**
     * HSTS configuration
     */
    public class Configuration {
        /**
         * Consents that the policy allows including the domain into web browser preloading list
         */
        public var preload: Boolean = false

        /**
         * Adds includeSubDomains directive, which applies this policy to this domain and any subdomains
         */
        public var includeSubDomains: Boolean = true

        /**
         * Duration in seconds to tell the client to keep the host in a list of known HSTS hosts.
         */
        public var maxAgeInSeconds: Long = DEFAULT_HSTS_MAX_AGE
            set(newMaxAge) {
                check(newMaxAge >= 0L) { "maxAgeInSeconds shouldn't be negative: $newMaxAge" }
                field = newMaxAge
            }

        /**
         * Any custom directives supported by specific user-agent
         */
        public val customDirectives: MutableMap<String, String?> = HashMap()
    }

    /**
     * Constructed `Strict-Transport-Security` header value
     */
    @Suppress("MemberVisibilityCanBePrivate")
    public val headerValue: String = buildString {
        append("max-age=")
        append(config.maxAgeInSeconds)

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
     * Plugin's main interceptor, usually installed by the plugin itself
     */
    public fun intercept(call: ApplicationCall) {
        if (call.request.origin.run { scheme == "https" && port == 443 }) {
            call.response.header(HttpHeaders.StrictTransportSecurity, headerValue)
        }
    }

    /**
     * A plugin installation object
     */
    public companion object Plugin : RouteScopedPlugin<Configuration, HSTS> {
        public const val DEFAULT_HSTS_MAX_AGE: Long = 365L * 24 * 3600 // 365 days

        override val key: AttributeKey<HSTS> = AttributeKey("HSTS")
        override fun install(
            pipeline: ApplicationCallPipeline,
            configure: Configuration.() -> Unit
        ): HSTS {
            val plugin = HSTS(Configuration().apply(configure))
            pipeline.intercept(ApplicationCallPipeline.Plugins) { plugin.intercept(call) }
            return plugin
        }
    }
}
