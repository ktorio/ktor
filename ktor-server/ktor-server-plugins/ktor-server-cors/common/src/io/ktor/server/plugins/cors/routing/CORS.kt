/*
 * Copyright 2014-2022 JetBrains s.r.o and contributors. Use of this source code is governed by the Apache 2.0 license.
 */

package io.ktor.server.plugins.cors.routing

import io.ktor.http.ContentType
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpMethod
import io.ktor.http.HttpStatusCode
import io.ktor.server.application.*
import io.ktor.server.plugins.cors.*
import io.ktor.server.plugins.origin
import io.ktor.server.request.header
import io.ktor.server.request.httpMethod
import io.ktor.server.request.uri
import io.ktor.server.response.respond
import io.ktor.server.routing.HttpMethodRouteSelector
import io.ktor.server.routing.Route
import io.ktor.server.routing.RouteSelector
import io.ktor.server.routing.RouteSelectorEvaluation
import io.ktor.server.routing.RoutingResolveContext
import io.ktor.server.routing.application
import io.ktor.server.routing.get
import io.ktor.server.routing.interceptCreateChild
import io.ktor.server.routing.options
import io.ktor.server.routing.route
import io.ktor.util.toLowerCasePreservingASCIIRules
import io.ktor.utils.io.InternalAPI

/**
 * A plugin that allows you to configure handling cross-origin requests.
 * This plugin allows you to configure allowed hosts, HTTP methods, headers set by the client, and so on.
 *
 * The configuration below allows requests from the specified address and allows sending the `Content-Type` header:
 * ```kotlin
 * install(CORS) {
 *     host("0.0.0.0:8081")
 *     header(HttpHeaders.ContentType)
 * }
 * ```
 *
 * You can learn more from [CORS](https://ktor.io/docs/cors.html).
 *
 * [Report a problem](https://ktor.io/feedback/?fqname=io.ktor.server.plugins.cors.routing.CORS)
 */
public val CORS: RouteScopedPlugin<CORSConfig> = createRouteScopedPlugin("CORS", ::CORSConfig) {
    buildPlugin()
}

@OptIn(InternalAPI::class)
public fun Route.cors(configure: CORSConfig.() -> Unit = {}) {
    val thisRoute = this

    val pluginConfig = CORSConfig().apply(configure)

    val allowsAnyHost: Boolean = "*" in pluginConfig.hosts
    val allowCredentials: Boolean = pluginConfig.allowCredentials
    val methods: Set<HttpMethod> = HashSet(pluginConfig.methods + CORSConfig.CorsDefaultMethods)
    val allowNonSimpleContentTypes: Boolean = pluginConfig.allowNonSimpleContentTypes
    val allHeaders: Set<String> =
        (pluginConfig.headers + CORSConfig.CorsSimpleRequestHeaders).let { headers ->
            if (pluginConfig.allowNonSimpleContentTypes) headers else headers.minus(HttpHeaders.ContentType)
        }
    val headerPredicates: List<(String) -> Boolean> = pluginConfig.headerPredicates
    val headersList = pluginConfig.headers.filterNot { it in CORSConfig.CorsSimpleRequestHeaders }
        .let { if (allowNonSimpleContentTypes) it + HttpHeaders.ContentType else it }
    val allHeadersSet: Set<String> = allHeaders.map { it.toLowerCasePreservingASCIIRules() }.toSet()
    val methodsListHeaderValue = methods.filterNot { it in CORSConfig.CorsDefaultMethods }
        .map { it.value }
        .sorted()
        .joinToString(", ")
    val maxAgeHeaderValue = pluginConfig.maxAgeInSeconds.let { if (it > 0) it.toString() else null }

    application.interceptCreateChild { parentRoute, newRoute ->
        val selector = newRoute.selector
        if (selector is HttpMethodRouteSelector && selector.method != HttpMethod.Options) {
            val optionsRoute = parentRoute.children.find {
                val selector = it.selector
                selector is HttpMethodRouteSelector && selector.method == HttpMethod.Options
            }

            if (optionsRoute == null) {
                var parent: Route? = parentRoute
                var isInner = false

                while (parent != null) {
                    if (parent == thisRoute) {
                        isInner = true
                        break
                    }

                    parent = parent.parent
                }

                if (isInner) {
                    parentRoute.options {
                        val origin = call.request.headers.getAll(HttpHeaders.Origin)?.singleOrNull()

                        if (origin != null) {
                            call.respondPreflight(
                                origin,
                                methodsListHeaderValue,
                                headersList,
                                methods,
                                allowsAnyHost,
                                allowCredentials,
                                maxAgeHeaderValue,
                                headerPredicates,
                                allHeadersSet
                            )
                        }
                    }
                }
            }
        }
    }

    install(CORS) {
        configure()
    }
}
