/*
 * Copyright 2014-2022 JetBrains s.r.o and contributors. Use of this source code is governed by the Apache 2.0 license.
 */

package io.ktor.server.plugins.cors

import io.ktor.http.*
import io.ktor.server.application.*
import io.ktor.server.plugins.*
import io.ktor.server.request.*
import io.ktor.server.response.*
import io.ktor.util.*

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
 */
@Deprecated(
    message = "This plugin was moved to io.ktor.server.plugins.cors.routing",
    level = DeprecationLevel.WARNING,
    replaceWith = ReplaceWith("CORS", "io.ktor.server.plugins.cors.routing.CORS")
)
public val CORS: ApplicationPlugin<CORSConfig> = createApplicationPlugin("CORS", ::CORSConfig) {
    buildPlugin()
}

internal fun PluginBuilder<CORSConfig>.buildPlugin() {
    val numberRegex = "[0-9]+".toRegex()
    val allowSameOrigin: Boolean = pluginConfig.allowSameOrigin
    val allowsAnyHost: Boolean = "*" in pluginConfig.hosts
    val allowCredentials: Boolean = pluginConfig.allowCredentials
    val allHeaders: Set<String> =
        (pluginConfig.headers + CORSConfig.CorsSimpleRequestHeaders).let { headers ->
            if (pluginConfig.allowNonSimpleContentTypes) headers else headers.minus(HttpHeaders.ContentType)
        }
    val originPredicates: List<(String) -> Boolean> = pluginConfig.originPredicates
    val headerPredicates: List<(String) -> Boolean> = pluginConfig.headerPredicates
    val methods: Set<HttpMethod> = HashSet(pluginConfig.methods + CORSConfig.CorsDefaultMethods)
    val allHeadersSet: Set<String> = allHeaders.map { it.toLowerCasePreservingASCIIRules() }.toSet()
    val allowNonSimpleContentTypes: Boolean = pluginConfig.allowNonSimpleContentTypes
    val headersList = pluginConfig.headers.filterNot { it in CORSConfig.CorsSimpleRequestHeaders }
        .let { if (allowNonSimpleContentTypes) it + HttpHeaders.ContentType else it }
    val methodsListHeaderValue = methods.filterNot { it in CORSConfig.CorsDefaultMethods }
        .map { it.value }
        .sorted()
        .joinToString(", ")
    val maxAgeHeaderValue = pluginConfig.maxAgeInSeconds.let { if (it > 0) it.toString() else null }
    val exposedHeaders = when {
        pluginConfig.exposedHeaders.isNotEmpty() -> pluginConfig.exposedHeaders.sorted().joinToString(", ")
        else -> null
    }
    val hostsNormalized = HashSet(
        pluginConfig.hosts
            .filterNot { it.contains('*') }
            .map { normalizeOrigin(it, numberRegex) }
    )
    val hostsWithWildcard = HashSet(
        pluginConfig.hosts
            .filter { it.contains('*') }
            .map {
                val normalizedOrigin = normalizeOrigin(it, numberRegex)
                val (prefix, suffix) = normalizedOrigin.split('*')
                prefix to suffix
            }
    )

    /**
     * A plugin's [call] interceptor that does all the job. Usually there is no need to install it as it is done during
     * a plugin installation.
     */
    onCall { call ->
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
            originPredicates,
            numberRegex
        )
        when (checkOrigin) {
            OriginCheckResult.OK -> {
            }
            OriginCheckResult.SkipCORS -> return@onCall
            OriginCheckResult.Failed -> {
                call.respondCorsFailed()
                return@onCall
            }
        }

        if (!allowNonSimpleContentTypes) {
            val contentType = call.request.header(HttpHeaders.ContentType)?.let { ContentType.parse(it) }
            if (contentType != null) {
                if (contentType.withoutParameters() !in CORSConfig.CorsSimpleContentTypes) {
                    call.respondCorsFailed()
                    return@onCall
                }
            }
        }

        if (call.request.httpMethod == HttpMethod.Options) {
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
            return@onCall
        }

        if (!call.corsCheckCurrentMethod(methods)) {
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

private enum class OriginCheckResult {
    OK, SkipCORS, Failed
}

private fun checkOrigin(
    origin: String,
    point: RequestConnectionPoint,
    allowSameOrigin: Boolean,
    allowsAnyHost: Boolean,
    hostsNormalized: Set<String>,
    hostsWithWildcard: Set<Pair<String, String>>,
    originPredicates: List<(String) -> Boolean>,
    numberRegex: Regex
): OriginCheckResult =
    when {
        !isValidOrigin(origin) -> OriginCheckResult.SkipCORS
        allowSameOrigin && isSameOrigin(origin, point, numberRegex) -> OriginCheckResult.SkipCORS
        !corsCheckOrigins(
            origin,
            allowsAnyHost,
            hostsNormalized,
            hostsWithWildcard,
            originPredicates,
            numberRegex
        ) -> OriginCheckResult.Failed
        else -> OriginCheckResult.OK
    }

private suspend fun ApplicationCall.respondPreflight(
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
    val requestHeaders =
        request.headers.getAll(HttpHeaders.AccessControlRequestHeaders)?.flatMap { it.split(",") }
            ?.filter { it.isNotBlank() }
            ?.map {
                it.trim().toLowerCasePreservingASCIIRules()
            } ?: emptyList()

    if (!corsCheckRequestMethod(methods) || !corsCheckRequestHeaders(requestHeaders, allHeadersSet, headerPredicates)) {
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

private fun ApplicationCall.accessControlAllowOrigin(
    origin: String,
    allowsAnyHost: Boolean,
    allowCredentials: Boolean
) {
    if (allowsAnyHost && !allowCredentials) {
        response.header(HttpHeaders.AccessControlAllowOrigin, "*")
    } else {
        response.header(HttpHeaders.AccessControlAllowOrigin, origin)
    }
}

private fun ApplicationCall.corsVary() {
    val vary = response.headers[HttpHeaders.Vary]
    if (vary == null) {
        response.header(HttpHeaders.Vary, HttpHeaders.Origin)
    } else {
        response.header(HttpHeaders.Vary, vary + ", " + HttpHeaders.Origin)
    }
}

private fun ApplicationCall.accessControlAllowCredentials(allowCredentials: Boolean) {
    if (allowCredentials) {
        response.header(HttpHeaders.AccessControlAllowCredentials, "true")
    }
}

private fun ApplicationCall.accessControlMaxAge(maxAgeHeaderValue: String?) {
    if (maxAgeHeaderValue != null) {
        response.header(HttpHeaders.AccessControlMaxAge, maxAgeHeaderValue)
    }
}

private fun isSameOrigin(origin: String, point: RequestConnectionPoint, numberRegex: Regex): Boolean {
    val requestOrigin = "${point.scheme}://${point.host}:${point.port}"
    return normalizeOrigin(requestOrigin, numberRegex) == normalizeOrigin(origin, numberRegex)
}

private fun corsCheckOrigins(
    origin: String,
    allowsAnyHost: Boolean,
    hostsNormalized: Set<String>,
    hostsWithWildcard: Set<Pair<String, String>>,
    originPredicates: List<(String) -> Boolean>,
    numberRegex: Regex
): Boolean {
    val normalizedOrigin = normalizeOrigin(origin, numberRegex)
    return allowsAnyHost || normalizedOrigin in hostsNormalized || hostsWithWildcard.any { (prefix, suffix) ->
        normalizedOrigin.startsWith(prefix) && normalizedOrigin.endsWith(suffix)
    } || originPredicates.any { it(origin) }
}

private fun corsCheckRequestHeaders(
    requestHeaders: List<String>,
    allHeadersSet: Set<String>,
    headerPredicates: List<(String) -> Boolean>
): Boolean {
    return requestHeaders.all { header ->
        header in allHeadersSet || headerMatchesAPredicate(header, headerPredicates)
    }
}

private fun headerMatchesAPredicate(header: String, headerPredicates: List<(String) -> Boolean>): Boolean {
    return headerPredicates.any { it(header) }
}

private fun ApplicationCall.corsCheckCurrentMethod(methods: Set<HttpMethod>): Boolean {
    return request.httpMethod in methods
}

private fun ApplicationCall.corsCheckRequestMethod(methods: Set<HttpMethod>): Boolean {
    val requestMethod = request.header(HttpHeaders.AccessControlRequestMethod)?.let { HttpMethod(it) }
    return requestMethod != null && requestMethod in methods
}

private suspend fun ApplicationCall.respondCorsFailed() {
    respond(HttpStatusCode.Forbidden)
}

private fun isValidOrigin(origin: String): Boolean {
    if (origin.isEmpty()) return false
    if (origin == "null") return true
    if ("%" in origin) return false

    val protoDelimiter = origin.indexOf("://")
    if (protoDelimiter <= 0) return false

    val protoValid = origin[0].isLetter() && origin.subSequence(0, protoDelimiter).all { ch ->
        ch.isLetter() || ch.isDigit() || ch == '-' || ch == '+' || ch == '.'
    }

    if (!protoValid) return false

    var portIndex = origin.length
    for (index in protoDelimiter + 3 until origin.length) {
        val ch = origin[index]
        if (ch == ':' || ch == '/') {
            portIndex = index + 1
            break
        }
        if (ch == '?') return false
    }

    for (index in portIndex until origin.length) {
        if (!origin[index].isDigit()) return false
    }

    return true
}

private fun normalizeOrigin(origin: String, numberRegex: Regex) =
    if (origin == "null" || origin == "*") origin else StringBuilder(origin.length).apply {
        append(origin)

        if (!origin.substringAfterLast(":", "").matches(numberRegex)) {
            val port = when (origin.substringBefore(':')) {
                "http" -> "80"
                "https" -> "443"
                else -> null
            }

            if (port != null) {
                append(':')
                append(port)
            }
        }
    }.toString()
