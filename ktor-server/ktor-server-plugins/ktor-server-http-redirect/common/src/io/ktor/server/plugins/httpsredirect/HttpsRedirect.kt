/*
 * Copyright 2014-2022 JetBrains s.r.o and contributors. Use of this source code is governed by the Apache 2.0 license.
 */

package io.ktor.server.plugins.httpsredirect

import io.ktor.http.*
import io.ktor.server.application.*
import io.ktor.server.plugins.*
import io.ktor.server.response.*
import io.ktor.server.util.*
import io.ktor.utils.io.*

/**
 * A configuration for the [HttpsRedirect] plugin.
 *
 * [Report a problem](https://ktor.io/feedback/?fqname=io.ktor.server.plugins.httpsredirect.HttpsRedirectConfig)
 */
@KtorDsl
public class HttpsRedirectConfig {
    /**
     * Specifies an HTTPS port (443 by default) used to redirect HTTP requests.
     *
     * [Report a problem](https://ktor.io/feedback/?fqname=io.ktor.server.plugins.httpsredirect.HttpsRedirectConfig.sslPort)
     */
    public var sslPort: Int = URLProtocol.HTTPS.defaultPort

    /**
     * Specifies whether to use permanent or temporary redirect.
     *
     * [Report a problem](https://ktor.io/feedback/?fqname=io.ktor.server.plugins.httpsredirect.HttpsRedirectConfig.permanentRedirect)
     */
    public var permanentRedirect: Boolean = true

    /**
     * Allows you to disable redirection for calls matching specified conditions.
     *
     * [Report a problem](https://ktor.io/feedback/?fqname=io.ktor.server.plugins.httpsredirect.HttpsRedirectConfig.excludePredicates)
     */
    public val excludePredicates: MutableList<(ApplicationCall) -> Boolean> = ArrayList()

    /**
     * Allows you to disable redirection for calls with a path matching [pathPrefix].
     *
     * [Report a problem](https://ktor.io/feedback/?fqname=io.ktor.server.plugins.httpsredirect.HttpsRedirectConfig.excludePrefix)
     */
    public fun excludePrefix(pathPrefix: String) {
        exclude { call ->
            call.request.origin.uri.startsWith(pathPrefix)
        }
    }

    /**
     * Allows you to disable redirection for calls with a path matching [pathSuffix].
     *
     * [Report a problem](https://ktor.io/feedback/?fqname=io.ktor.server.plugins.httpsredirect.HttpsRedirectConfig.excludeSuffix)
     */
    public fun excludeSuffix(pathSuffix: String) {
        exclude { call ->
            call.request.origin.uri.endsWith(pathSuffix)
        }
    }

    /**
     * Allows you to disable redirection for calls matching the specified [predicate].
     *
     * [Report a problem](https://ktor.io/feedback/?fqname=io.ktor.server.plugins.httpsredirect.HttpsRedirectConfig.exclude)
     */
    public fun exclude(predicate: (call: ApplicationCall) -> Boolean) {
        excludePredicates.add(predicate)
    }
}

/**
 * A plugin that redirects all HTTP requests to the HTTPS counterpart before processing the call.
 *
 * The code snippet below shows how to configure the desired HTTPS port and
 * return `301 Moved Permanently` for the requested resource:
 * ```kotlin
 * install(HttpsRedirect) {
 *     sslPort = 8443
 *     permanentRedirect = true
 * }
 * ```
 *
 * You can learn more from [HttpsRedirect](https://ktor.io/docs/https-redirect.html).
 *
 * [Report a problem](https://ktor.io/feedback/?fqname=io.ktor.server.plugins.httpsredirect.HttpsRedirect)
 */
public val HttpsRedirect: ApplicationPlugin<HttpsRedirectConfig> = createApplicationPlugin(
    "HttpsRedirect",
    ::HttpsRedirectConfig
) {
    onCall { call ->
        if (call.response.isCommitted) {
            return@onCall
        }

        if (call.request.origin.scheme == "http" &&
            pluginConfig.excludePredicates.none { predicate -> predicate(call) }
        ) {
            val redirectUrl = call.url {
                protocol = URLProtocol.HTTPS
                port = pluginConfig.sslPort
            }
            if (!call.response.isCommitted) {
                call.respondRedirect(redirectUrl, pluginConfig.permanentRedirect)
            }
        }
    }
}
