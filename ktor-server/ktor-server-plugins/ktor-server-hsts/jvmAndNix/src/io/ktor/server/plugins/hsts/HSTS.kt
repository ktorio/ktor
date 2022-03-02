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
 * HSTS configuration
 */
@KtorDsl
public class HSTSConfig {
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

internal const val DEFAULT_HSTS_MAX_AGE: Long = 365L * 24 * 3600 // 365 days

/**
 * HSTS plugin that appends `Strict-Transport-Security` HTTP header to every response.
 * See https://ktor.io/docs/hsts.html for details
 * See RFC 6797 https://tools.ietf.org/html/rfc6797
 */
public val HSTS: RouteScopedPlugin<HSTSConfig> = createRouteScopedPlugin("HSTS", ::HSTSConfig) {
    /**
     * Constructed `Strict-Transport-Security` header value
     */
    val headerValue: String = buildString {
        append("max-age=")
        append(pluginConfig.maxAgeInSeconds)

        if (pluginConfig.includeSubDomains) {
            append("; includeSubDomains")
        }
        if (pluginConfig.preload) {
            append("; preload")
        }

        if (pluginConfig.customDirectives.isNotEmpty()) {
            pluginConfig.customDirectives.entries.joinTo(this, separator = "; ", prefix = "; ") {
                if (it.value != null) {
                    "${it.key.escapeIfNeeded()}=${it.value?.escapeIfNeeded()}"
                } else {
                    it.key.escapeIfNeeded()
                }
            }
        }
    }

    onCall { call ->
        if (call.request.origin.run { scheme == "https" && port == 443 }) {
            call.response.header(HttpHeaders.StrictTransportSecurity, headerValue)
        }
    }
}
