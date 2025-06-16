/*
 * Copyright 2014-2022 JetBrains s.r.o and contributors. Use of this source code is governed by the Apache 2.0 license.
 */

package io.ktor.server.plugins.cors.routing

import io.ktor.http.ContentType
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpMethod
import io.ktor.server.application.*
import io.ktor.server.plugins.cors.*
import io.ktor.server.plugins.origin
import io.ktor.server.request.header
import io.ktor.server.routing.HttpMethodRouteSelector
import io.ktor.server.routing.Route
import io.ktor.server.routing.RoutingNode
import io.ktor.server.routing.RoutingRoot
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
@Deprecated(
    message = "???",
    level = DeprecationLevel.ERROR,
//    replaceWith = ReplaceWith("CORS", "io.ktor.server.plugins.cors.cors")
)
public val CORS: RouteScopedPlugin<CORSConfig> = createRouteScopedPlugin("CORS", ::CORSConfig) {
    buildPlugin()
}

private val privateCORS: RouteScopedPlugin<CORSConfig> = createRouteScopedPlugin("CORS", ::CORSConfig) {
    buildPlugin()
}

@OptIn(InternalAPI::class)
public fun Application.cors(configure: CORSConfig.() -> Unit = {}) {
    monitor.subscribe(ApplicationStarted) {
        val thisRoute = plugin(RoutingRoot)

        val pluginConfig = CORSConfig().apply(configure)

        val allowsAnyHost: Boolean = "*" in pluginConfig.hosts
        val allowSameOrigin: Boolean = pluginConfig.allowSameOrigin
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

        val hostsNormalized = HashSet(
            pluginConfig.hosts
                .filterNot { it.contains('*') }
                .map { normalizeOrigin(it) }
        )
        val hostsWithWildcard = HashSet(
            pluginConfig.hosts
                .filter { it.contains('*') }
                .map {
                    val normalizedOrigin = normalizeOrigin(it)
                    val (prefix, suffix) = normalizedOrigin.split('*')
                    prefix to suffix
                }
        )
        val originPredicates: List<(String) -> Boolean> = pluginConfig.originPredicates

        thisRoute.interceptChildCreation { parentNode, childNode ->
            val selector = childNode.selector
            if (selector is HttpMethodRouteSelector && selector.method != HttpMethod.Options) {
                val optionsRoute = parentNode.children.find {
                    val selector = it.selector
                    selector is HttpMethodRouteSelector && selector.method == HttpMethod.Options
                }

                if (optionsRoute == null) {
                    var parent: Route? = parentNode
                    var isInner = false

                    while (parent != null) {
                        if (parent == thisRoute) {
                            isInner = true
                            break
                        }

                        parent = parent.parent
                    }

                    if (isInner) {
                        val selector = HttpMethodRouteSelector(HttpMethod.Options)
                        parentNode.createChild(selector, notify = false).handle {
                            if (!allowsAnyHost || allowCredentials) {
                                call.corsVary()
                            }

                            val origin = call.request.headers.getAll(HttpHeaders.Origin)?.singleOrNull() ?: return@handle

                            val checkOrigin = checkOrigin(
                                origin,
                                call.request.origin,
                                allowSameOrigin,
                                allowsAnyHost,
                                hostsNormalized,
                                hostsWithWildcard,
                                originPredicates
                            )
                            when (checkOrigin) {
                                OriginCheckResult.OK -> {
                                }

                                OriginCheckResult.SkipCORS -> return@handle
                                OriginCheckResult.Failed -> {
//                                    LOGGER.trace("Respond forbidden ${call.request.uri}: origin doesn't match ${call.request.origin}")
                                    call.respondCorsFailed()
                                    return@handle
                                }
                            }

                            if (!allowNonSimpleContentTypes) {
                                val contentType = call.request.header(HttpHeaders.ContentType)?.let { ContentType.parse(it) }
                                if (contentType != null) {
                                    if (contentType.withoutParameters() !in CORSConfig.CorsSimpleContentTypes) {
//                                        LOGGER.trace("Respond forbidden ${call.request.uri}: Content-Type isn't allowed $contentType")
                                        call.respondCorsFailed()
                                        return@handle
                                    }
                                }
                            }

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

//                        parentNode.options {
//                            val origin = call.request.headers.getAll(HttpHeaders.Origin)?.singleOrNull()


//                    }
                }
            }
        }
    }

    install(privateCORS) {
        configure()
    }
}

@OptIn(InternalAPI::class)
public fun Route.cors(configure: CORSConfig.() -> Unit = {}) {
    val thisRoute = this as RoutingNode

    val pluginConfig = CORSConfig().apply(configure)

    val allowsAnyHost: Boolean = "*" in pluginConfig.hosts
    val allowSameOrigin: Boolean = pluginConfig.allowSameOrigin
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

    val hostsNormalized = HashSet(
        pluginConfig.hosts
            .filterNot { it.contains('*') }
            .map { normalizeOrigin(it) }
    )
    val hostsWithWildcard = HashSet(
        pluginConfig.hosts
            .filter { it.contains('*') }
            .map {
                val normalizedOrigin = normalizeOrigin(it)
                val (prefix, suffix) = normalizedOrigin.split('*')
                prefix to suffix
            }
    )
    val originPredicates: List<(String) -> Boolean> = pluginConfig.originPredicates

    thisRoute.interceptChildCreation { parentNode, childNode ->
        val selector = childNode.selector
        if (selector is HttpMethodRouteSelector && selector.method != HttpMethod.Options) {
            val optionsRoute = parentNode.children.find {
                val selector = it.selector
                selector is HttpMethodRouteSelector && selector.method == HttpMethod.Options
            }

            if (optionsRoute == null) {
                var parent: Route? = parentNode
                var isInner = false

                while (parent != null) {
                    if (parent == thisRoute) {
                        isInner = true
                        break
                    }

                    parent = parent.parent
                }

                if (isInner) {
                    val selector = HttpMethodRouteSelector(HttpMethod.Options)
                    parentNode.createChild(selector, notify = false).handle {
                        if (!allowsAnyHost || allowCredentials) {
                            call.corsVary()
                        }

                        val origin = call.request.headers.getAll(HttpHeaders.Origin)?.singleOrNull() ?: return@handle

                        val checkOrigin = checkOrigin(
                            origin,
                            call.request.origin,
                            allowSameOrigin,
                            allowsAnyHost,
                            hostsNormalized,
                            hostsWithWildcard,
                            originPredicates
                        )
                        when (checkOrigin) {
                            OriginCheckResult.OK -> {
                            }

                            OriginCheckResult.SkipCORS -> return@handle
                            OriginCheckResult.Failed -> {
//                                    LOGGER.trace("Respond forbidden ${call.request.uri}: origin doesn't match ${call.request.origin}")
                                call.respondCorsFailed()
                                return@handle
                            }
                        }

                        if (!allowNonSimpleContentTypes) {
                            val contentType = call.request.header(HttpHeaders.ContentType)?.let { ContentType.parse(it) }
                            if (contentType != null) {
                                if (contentType.withoutParameters() !in CORSConfig.CorsSimpleContentTypes) {
//                                        LOGGER.trace("Respond forbidden ${call.request.uri}: Content-Type isn't allowed $contentType")
                                    call.respondCorsFailed()
                                    return@handle
                                }
                            }
                        }

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
//                        parentNode.options {
//                            val origin = call.request.headers.getAll(HttpHeaders.Origin)?.singleOrNull()


//                    }
                }
            }
        }
    }

//    application.interceptCreateChild { parentRoute, newRoute ->
//
//    }

    install(privateCORS) {
        configure()
    }
}
