/*
 * Copyright 2014-2022 JetBrains s.r.o and contributors. Use of this source code is governed by the Apache 2.0 license.
 */

package io.ktor.server.plugins.hsts

import io.ktor.http.*
import io.ktor.server.application.*
import io.ktor.server.plugins.*
import io.ktor.server.request.*
import io.ktor.server.response.*
import io.ktor.utils.io.*

/**
 *  A configuration for the [HSTS] settings for a host.
 *
 * [Report a problem](https://ktor.io/feedback/?fqname=io.ktor.server.plugins.hsts.HSTSHostConfig)
 */
@KtorDsl
public open class HSTSHostConfig {
    /**
     * Specifies the `preload` HSTS directive, which allows you to include your domain name
     * in the HSTS preload list.
     *
     * [Report a problem](https://ktor.io/feedback/?fqname=io.ktor.server.plugins.hsts.HSTSHostConfig.preload)
     */
    public var preload: Boolean = false

    /**
     * Specifies the `includeSubDomains` directive, which applies this policy to any subdomains as well.
     *
     * [Report a problem](https://ktor.io/feedback/?fqname=io.ktor.server.plugins.hsts.HSTSHostConfig.includeSubDomains)
     */
    public var includeSubDomains: Boolean = true

    /**
     * Specifies how long (in seconds) the client should keep the host in a list of known HSTS hosts:
     *
     * [Report a problem](https://ktor.io/feedback/?fqname=io.ktor.server.plugins.hsts.HSTSHostConfig.maxAgeInSeconds)
     */
    public var maxAgeInSeconds: Long = DEFAULT_HSTS_MAX_AGE
        set(newMaxAge) {
            check(newMaxAge >= 0L) { "maxAgeInSeconds shouldn't be negative: $newMaxAge" }
            field = newMaxAge
        }

    /**
     * Allows you to add custom directives supported by a specific user agent.
     *
     * [Report a problem](https://ktor.io/feedback/?fqname=io.ktor.server.plugins.hsts.HSTSHostConfig.customDirectives)
     */
    public val customDirectives: MutableMap<String, String?> = HashMap()
}

/**
 *  A configuration for the [HSTS] plugin.
 *
 * [Report a problem](https://ktor.io/feedback/?fqname=io.ktor.server.plugins.hsts.HSTSConfig)
 */
@KtorDsl
public class HSTSConfig : HSTSHostConfig() {
    /**
     * @see [withHost]
     */
    internal val hostSpecific: MutableMap<String, HSTSHostConfig> = HashMap()

    internal var filter: ((ApplicationCall) -> Boolean)? = null

    /**
     * Set specific configuration for a [host].
     *
     * [Report a problem](https://ktor.io/feedback/?fqname=io.ktor.server.plugins.hsts.HSTSConfig.withHost)
     */
    public fun withHost(host: String, configure: HSTSHostConfig.() -> Unit) {
        this.hostSpecific[host] = HSTSHostConfig().apply(configure)
    }

    /**
     * Sets a filter that determines whether the plugin should be applied to a specific call.
     *
     * [Report a problem](https://ktor.io/feedback/?fqname=io.ktor.server.plugins.hsts.HSTSConfig.filter)
     */
    public fun filter(block: (ApplicationCall) -> Boolean) {
        this.filter = block
    }
}

internal const val DEFAULT_HSTS_MAX_AGE: Long = 365L * 24 * 3600 // 365 days

/**
 * A plugin that appends the `Strict-Transport-Security` HTTP header to every response.
 *
 * The [HSTS] configuration below specifies how long the client
 * should keep the host in a list of known HSTS hosts:
 * ```kotlin
 * install(HSTS) {
 *     maxAgeInSeconds = 10
 * }
 * ```
 * You can learn more from [HSTS](https://ktor.io/docs/hsts.html).
 *
 * [Report a problem](https://ktor.io/feedback/?fqname=io.ktor.server.plugins.hsts.HSTS)
 */
public val HSTS: RouteScopedPlugin<HSTSConfig> = createRouteScopedPlugin("HSTS", ::HSTSConfig) {
    fun constructHeaderValue(config: HSTSHostConfig) = buildString {
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
     * A constructed default `Strict-Transport-Security` header value.
     */
    val headerValue: String = constructHeaderValue(pluginConfig)

    val hostHeaderValues: Map<String, String> = pluginConfig.hostSpecific.mapValues { constructHeaderValue(it.value) }

    val filter = pluginConfig.filter ?: { call ->
        call.request.origin.run { scheme == "https" && serverPort == 443 }
    }

    onCallRespond { call ->
        if (filter(call)) {
            call.response.header(
                HttpHeaders.StrictTransportSecurity,
                hostHeaderValues[call.request.host()] ?: headerValue
            )
        }
    }
}
