/*
 * Copyright 2014-2024 JetBrains s.r.o and contributors. Use of this source code is governed by the Apache 2.0 license.
 */

package io.ktor.server.plugins.cors

import io.ktor.http.*
import io.ktor.server.application.*
import io.ktor.server.plugins.*
import io.ktor.server.plugins.cors.routing.corsPlugin
import io.ktor.server.request.*
import io.ktor.server.response.*
import io.ktor.server.routing.HttpMethodRouteSelector
import io.ktor.server.routing.Route
import io.ktor.server.routing.RoutingNode
import io.ktor.server.routing.RoutingRoot
import io.ktor.util.*
import io.ktor.util.logging.*
import io.ktor.utils.io.InternalAPI

private val LOGGER = KtorSimpleLogger("io.ktor.server.plugins.cors.CORS")

/**
 * A plugin that allows you to configure handling cross-origin requests.
 * This plugin allows you to configure allowed hosts, HTTP methods, headers set by the client, and so on.
 *
 * The configuration below allows requests from the specified address and allows sending the `Content-Type` header:
 * ```kotlin
 * install(CORS) {
 *     allowHost("0.0.0.0:8081")
 *     allowHeader(HttpHeaders.ContentType)
 * }
 * ```
 *
 * You can learn more from [CORS](https://ktor.io/docs/cors.html).
 *
 * [Report a problem](https://ktor.io/feedback/?fqname=io.ktor.server.plugins.cors.CORS)
 */
@Deprecated(
    message = "This plugin was moved to io.ktor.server.plugins.cors.routing",
    level = DeprecationLevel.ERROR,
    replaceWith = ReplaceWith("CORS", "io.ktor.server.plugins.cors.routing.CORS")
)
public val CORS: ApplicationPlugin<CORSConfig> = createApplicationPlugin("CORS", ::CORSConfig) {
    buildPlugin()
}

@OptIn(InternalAPI::class)
public fun Application.cors(configure: CORSConfig.() -> Unit = {}) {
    monitor.subscribe(ApplicationStarted) {
        val route = pluginOrNull(RoutingRoot)
        configureRoutesAndInstallPlugin(this, route, configure)
    }
}

@OptIn(InternalAPI::class)
public fun Route.cors(configure: CORSConfig.() -> Unit = {}) {
    val node = this as RoutingNode
    configureRoutesAndInstallPlugin(node, node, configure)
}

@OptIn(InternalAPI::class)
private fun configureRoutesAndInstallPlugin(pipeline: ApplicationCallPipeline, route: RoutingNode?, configure: CORSConfig.() -> Unit) {
    val pluginConfig = CORSConfig().apply(configure)

    val methods: Set<HttpMethod> = HashSet(pluginConfig.methods + CORSConfig.CorsDefaultMethods)
    val allowNonSimpleContentTypes: Boolean = pluginConfig.allowNonSimpleContentTypes
    val allHeaders: Set<String> =
        (pluginConfig.headers + CORSConfig.CorsSimpleRequestHeaders).let { headers ->
            if (pluginConfig.allowNonSimpleContentTypes) headers else headers.minus(HttpHeaders.ContentType)
        }
    val headersList = pluginConfig.headers.filterNot { it in CORSConfig.CorsSimpleRequestHeaders }
        .let { if (allowNonSimpleContentTypes) it + HttpHeaders.ContentType else it }
    val allHeadersSet: Set<String> = allHeaders.map { it.toLowerCasePreservingASCIIRules() }.toSet()
    val methodsListHeaderValue = methods.filterNot { it in CORSConfig.CorsDefaultMethods }
        .map { it.value }
        .sorted()
        .joinToString(", ")

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

    route?.interceptChildCreation { parentNode, childNode ->
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
                    if (parent == route) {
                        isInner = true
                        break
                    }

                    parent = parent.parent
                }

                if (isInner) {
                    val selector = HttpMethodRouteSelector(HttpMethod.Options)
                    parentNode.createChild(selector, notify = false).handle {
                        val allowsAnyHost: Boolean = "*" in pluginConfig.hosts

                        if (!allowsAnyHost || pluginConfig.allowCredentials) {
                            call.corsVary()
                        }

                        val origin = call.request.headers.getAll(HttpHeaders.Origin)?.singleOrNull()

                        if (origin != null) {
                            val checkOrigin = checkOrigin(
                                origin,
                                call.request.origin,
                                pluginConfig.allowSameOrigin,
                                allowsAnyHost,
                                hostsNormalized,
                                hostsWithWildcard,
                                pluginConfig.originPredicates
                            )

                            when (checkOrigin) {
                                OriginCheckResult.OK -> {
                                    var simpleTypesCheckFailed = false
                                    if (!pluginConfig.allowNonSimpleContentTypes) {
                                        val contentType = call.request.header(HttpHeaders.ContentType)?.let { ContentType.parse(it) }
                                        if (contentType != null) {
                                            if (contentType.withoutParameters() !in CORSConfig.CorsSimpleContentTypes) {
                                                LOGGER.trace("Respond forbidden ${call.request.uri}: Content-Type isn't allowed $contentType")
                                                simpleTypesCheckFailed = true
                                            }
                                        }
                                    }

                                    if (!simpleTypesCheckFailed) {
                                        val maxAgeHeaderValue = pluginConfig.maxAgeInSeconds.let { if (it > 0) it.toString() else null }

                                        call.respondPreflight(
                                            origin,
                                            methodsListHeaderValue,
                                            headersList,
                                            methods,
                                            allowsAnyHost,
                                            pluginConfig.allowCredentials,
                                            maxAgeHeaderValue,
                                            pluginConfig.headerPredicates,
                                            allHeadersSet
                                        )
                                    } else {
                                        call.respondCorsFailed()
                                    }
                                }

                                OriginCheckResult.SkipCORS -> {}
                                OriginCheckResult.Failed -> {
                                    LOGGER.trace("Respond forbidden ${call.request.uri}: origin doesn't match ${call.request.origin}")
                                    call.respondCorsFailed()
                                }
                            }
                        }
                    }
                }
            }
        }
    }

    pipeline.install(corsPlugin, configure)
}


internal fun PluginBuilder<CORSConfig>.buildPlugin() {
    val allowSameOrigin: Boolean = pluginConfig.allowSameOrigin
    val allowsAnyHost: Boolean = "*" in pluginConfig.hosts
    val allowCredentials: Boolean = pluginConfig.allowCredentials
    val originPredicates: List<(String) -> Boolean> = pluginConfig.originPredicates
    val methods: Set<HttpMethod> = HashSet(pluginConfig.methods + CORSConfig.CorsDefaultMethods)
    val allowNonSimpleContentTypes: Boolean = pluginConfig.allowNonSimpleContentTypes
    val exposedHeaders = when {
        pluginConfig.exposedHeaders.isNotEmpty() -> pluginConfig.exposedHeaders.sorted().joinToString(", ")
        else -> null
    }
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

    /**
     * A plugin's [call] interceptor that does all the job. Usually there is no need to install it as it is done during
     * a plugin installation.
     */
    onCall { call ->
        if (call.response.isCommitted || call.request.httpMethod == HttpMethod.Options) {
            return@onCall
        }

        if (!allowsAnyHost || allowCredentials) {
            call.corsVary()
        }

        val origin = call.request.headers.getAll(HttpHeaders.Origin)?.singleOrNull() ?: return@onCall

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

            OriginCheckResult.SkipCORS -> return@onCall
            OriginCheckResult.Failed -> {
                LOGGER.trace("Respond forbidden ${call.request.uri}: origin doesn't match ${call.request.origin}")
                call.respondCorsFailed()
                return@onCall
            }
        }

        if (!allowNonSimpleContentTypes) {
            val contentType = call.request.header(HttpHeaders.ContentType)?.let { ContentType.parse(it) }
            if (contentType != null) {
                if (contentType.withoutParameters() !in CORSConfig.CorsSimpleContentTypes) {
                    LOGGER.trace("Respond forbidden ${call.request.uri}: Content-Type isn't allowed $contentType")
                    call.respondCorsFailed()
                    return@onCall
                }
            }
        }

        if (!call.corsCheckCurrentMethod(methods)) {
            LOGGER.trace("Respond forbidden ${call.request.uri}: method doesn't match ${call.request.httpMethod}")
            call.respondCorsFailed()
            return@onCall
        }

        call.accessControlAllowOrigin(origin, allowsAnyHost, allowCredentials)
        call.accessControlAllowCredentials(allowCredentials)

        if (exposedHeaders != null) {
            call.response.header(HttpHeaders.AccessControlExposeHeaders, exposedHeaders)
        }
    }
}

internal enum class OriginCheckResult {
    OK,
    SkipCORS,
    Failed,
}

internal fun checkOrigin(
    origin: String,
    point: RequestConnectionPoint,
    allowSameOrigin: Boolean,
    allowsAnyHost: Boolean,
    hostsNormalized: Set<String>,
    hostsWithWildcard: Set<Pair<String, String>>,
    originPredicates: List<(String) -> Boolean>
): OriginCheckResult = when {
    !isValidOrigin(origin) -> OriginCheckResult.SkipCORS
    allowSameOrigin && isSameOrigin(origin, point) -> OriginCheckResult.SkipCORS
    !corsCheckOrigins(
        origin,
        allowsAnyHost,
        hostsNormalized,
        hostsWithWildcard,
        originPredicates
    ) -> OriginCheckResult.Failed

    else -> OriginCheckResult.OK
}

internal suspend fun ApplicationCall.respondPreflight(
    origin: String,
    methodsListHeaderValue: String,
    headersList: List<String>,
    methods: Set<HttpMethod>,
    allowsAnyHost: Boolean,
    allowCredentials: Boolean,
    maxAgeHeaderValue: String?,
    headerPredicates: List<(String) -> Boolean>,
    allHeadersSet: Set<String>
) {
    val requestHeaders = request.headers
        .getAll(HttpHeaders.AccessControlRequestHeaders)
        ?.flatMap { it.split(",") }
        ?.filter { it.isNotBlank() }
        ?.map {
            it.trim().toLowerCasePreservingASCIIRules()
        } ?: emptyList()

    if (!corsCheckRequestMethod(methods)) {
        LOGGER.trace("Return Forbidden for ${this.request.uri}: CORS method doesn't match ${request.httpMethod}")
        respond(HttpStatusCode.Forbidden)
        return
    }

    if (!corsCheckRequestHeaders(requestHeaders, allHeadersSet, headerPredicates)) {
        LOGGER.trace("Return Forbidden for ${this.request.uri}: request has not allowed headers.")
        respond(HttpStatusCode.Forbidden)
        return
    }

    accessControlAllowOrigin(origin, allowsAnyHost, allowCredentials)
    accessControlAllowCredentials(allowCredentials)
    if (methodsListHeaderValue.isNotEmpty()) {
        response.header(HttpHeaders.AccessControlAllowMethods, methodsListHeaderValue)
    }

    val requestHeadersMatchingPrefix = requestHeaders
        .filter { header -> headerMatchesAPredicate(header, headerPredicates) }

    val headersListHeaderValue = (headersList + requestHeadersMatchingPrefix).sorted().joinToString(", ")

    response.header(HttpHeaders.AccessControlAllowHeaders, headersListHeaderValue)
    accessControlMaxAge(maxAgeHeaderValue)

    respond(HttpStatusCode.OK)
}
