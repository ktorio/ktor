/*
 * Copyright 2014-2022 JetBrains s.r.o and contributors. Use of this source code is governed by the Apache 2.0 license.
 */

@file:Suppress("DEPRECATION_ERROR")

package io.ktor.server.plugins.cors

import io.ktor.http.*
import io.ktor.server.application.*
import io.ktor.server.plugins.*
import io.ktor.server.request.*
import io.ktor.server.response.*
import io.ktor.util.*

/**
 * A CORS plugin that allows you to configure allowed hosts, HTTP methods, headers set by the client, and so on.
 */
public val CORS: ApplicationPlugin<Application, CORSConfig, PluginInstance> =
    createApplicationPlugin("CORS", ::CORSConfig) {
        val numberRegex = "[0-9]+".toRegex()
        val allowSameOrigin: Boolean = pluginConfig.allowSameOrigin
        val allowsAnyHost: Boolean = "*" in pluginConfig.hosts
        val allowCredentials: Boolean = pluginConfig.allowCredentials
        val allHeaders: Set<String> =
            (pluginConfig.headers + CORSConfig.CorsSimpleRequestHeaders).let { headers ->
                if (pluginConfig.allowNonSimpleContentTypes) headers else headers.minus(HttpHeaders.ContentType)
            }
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
         * Plugin's call interceptor that does all the job. Usually there is no need to install it as it is done during
         * plugin installation
         */
        /**
         * Plugin's call interceptor that does all the job. Usually there is no need to install it as it is done during
         * plugin installation
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

/**
 * CORS plugin configuration
 */
@KtorDsl
public class CORSConfig {
    private val wildcardWithDot = "*."

    public companion object {

        /**
         * The default CORS max age value
         */
        public const val CORS_DEFAULT_MAX_AGE: Long = 24L * 3600 // 1 day

        /**
         * Default HTTP methods that are always allowed by CORS
         */
        public val CorsDefaultMethods: Set<HttpMethod> = setOf(HttpMethod.Get, HttpMethod.Post, HttpMethod.Head)

        /**
         * Default HTTP headers that are always allowed by CORS
         * (simple request headers according to https://www.w3.org/TR/cors/#simple-header )
         * Please note that `Content-Type` header simplicity depends on it's value.
         */
        public val CorsSimpleRequestHeaders: Set<String> = caseInsensitiveSet(
            HttpHeaders.Accept,
            HttpHeaders.AcceptLanguage,
            HttpHeaders.ContentLanguage,
            HttpHeaders.ContentType
        )

        /**
         * Default HTTP headers that are always allowed by CORS to be used in response
         * (simple request headers according to https://www.w3.org/TR/cors/#simple-header )
         */
        public val CorsSimpleResponseHeaders: Set<String> = caseInsensitiveSet(
            HttpHeaders.CacheControl,
            HttpHeaders.ContentLanguage,
            HttpHeaders.ContentType,
            HttpHeaders.Expires,
            HttpHeaders.LastModified,
            HttpHeaders.Pragma
        )

        /**
         * The allowed set of content types that are allowed by CORS without preflight check
         */
        @Suppress("unused")
        public val CorsSimpleContentTypes: Set<ContentType> =
            setOf(
                ContentType.Application.FormUrlEncoded,
                ContentType.MultiPart.FormData,
                ContentType.Text.Plain
            ).unmodifiable()

        @OptIn(InternalAPI::class)
        private fun caseInsensitiveSet(vararg elements: String): Set<String> =
            CaseInsensitiveSet(elements.asList())
    }

    /**
     * Allowed CORS hosts
     */
    public val hosts: MutableSet<String> = HashSet()

    /**
     * Allowed CORS headers
     */
    @OptIn(InternalAPI::class)
    public val headers: MutableSet<String> = CaseInsensitiveSet()

    /**
     * Allowed HTTP methods
     */
    public val methods: MutableSet<HttpMethod> = HashSet()

    /**
     * Exposed HTTP headers that could be accessed by a client
     */
    @OptIn(InternalAPI::class)
    public val exposedHeaders: MutableSet<String> = CaseInsensitiveSet()

    /**
     * Allow sending credentials
     */
    public var allowCredentials: Boolean = false

    /**
     * If present represents the prefix for headers which are permitted in cors requests.
     */
    public val headerPredicates: MutableList<(String) -> Boolean> = mutableListOf()

    /**
     * Duration in seconds to tell the client to keep the host in a list of known HSTS hosts.
     */
    public var maxAgeInSeconds: Long = CORS_DEFAULT_MAX_AGE
        set(newMaxAge) {
            check(newMaxAge >= 0L) { "maxAgeInSeconds shouldn't be negative: $newMaxAge" }
            field = newMaxAge
        }

    /**
     * Allow requests from the same origin
     */
    public var allowSameOrigin: Boolean = true

    /**
     * Allow sending requests with non-simple content-types. The following content types are considered simple:
     * - `text/plain`
     * - `application/x-www-form-urlencoded`
     * - `multipart/form-data`
     */
    public var allowNonSimpleContentTypes: Boolean = false

    /**
     * Allow requests from any host
     */
    public fun anyHost() {
        hosts.add("*")
    }

    /**
     * Allow requests from the specified domains and schemes. A wildcard is supported for either the host or any
     * subdomain. If you specify a wildcard in the host, you cannot add specific subdomains. Otherwise you can mix
     * wildcard and non-wildcard subdomains as long as the wildcard is always in front of the domain,
     * e.g. `*.sub.domain.com` but not `sub.*.domain.com`.
     */
    public fun host(host: String, schemes: List<String> = listOf("http"), subDomains: List<String> = emptyList()) {
        if (host == "*") {
            return anyHost()
        }

        require("://" !in host) { "scheme should be specified as a separate parameter schemes" }

        for (schema in schemes) {
            addHost("$schema://$host")

            for (subDomain in subDomains) {
                validateWildcardRequirements(subDomain)
                addHost("$schema://$subDomain.$host")
            }
        }
    }

    private fun addHost(host: String) {
        validateWildcardRequirements(host)
        hosts.add(host)
    }

    private fun validateWildcardRequirements(host: String) {
        if ('*' !in host) {
            return
        }

        fun String.countMatches(subString: String): Int =
            windowed(subString.length) {
                if (it == subString) 1 else 0
            }.sum()

        require(wildcardInFrontOfDomain(host)) { "wildcard must appear in front of the domain, e.g. *.domain.com" }
        require(host.countMatches(wildcardWithDot) == 1) { "wildcard cannot appear more than once" }
    }

    private fun wildcardInFrontOfDomain(host: String): Boolean {
        val indexOfWildcard = host.indexOf(wildcardWithDot)
        return wildcardWithDot in host && !host.endsWith(wildcardWithDot) &&
            (indexOfWildcard <= 0 || host.substringBefore(wildcardWithDot).endsWith("://"))
    }

    /**
     * Allow to expose [header]. It adds the [header] to `Access-Control-Expose-Headers` if it is not a
     * simple response header.
     */
    public fun exposeHeader(header: String) {
        if (header !in CorsSimpleResponseHeaders) {
            exposedHeaders.add(header)
        }
    }

    /**
     * Allow to send `X-Http-Method-Override` header
     */
    @Suppress("unused")
    public fun allowXHttpMethodOverride() {
        header(HttpHeaders.XHttpMethodOverride)
    }

    /**
     * Allow headers prefixed with [headerPrefix]
     */
    public fun allowHeadersPrefixed(headerPrefix: String) {
        this.headerPredicates.add { name -> name.startsWith(headerPrefix) }
    }

    /**
     * Allow headers that match [predicate]
     */
    public fun allowHeaders(predicate: (String) -> Boolean) {
        this.headerPredicates.add(predicate)
    }

    /**
     * Allow sending [header]
     */
    public fun header(header: String) {
        if (header.equals(HttpHeaders.ContentType, ignoreCase = true)) {
            allowNonSimpleContentTypes = true
            return
        }

        if (header !in CorsSimpleRequestHeaders) {
            headers.add(header)
        }
    }

    /**
     * Please note that CORS operates ONLY with REAL HTTP methods
     * and will never consider overridden methods via `X-Http-Method-Override`.
     * However you can add them here if you are implementing CORS at client side from the scratch
     * that you generally don't need to do.
     */
    public fun method(method: HttpMethod) {
        if (method !in CorsDefaultMethods) {
            methods.add(method)
        }
    }
}

internal enum class OriginCheckResult {
    OK, SkipCORS, Failed
}

internal fun checkOrigin(
    origin: String,
    point: RequestConnectionPoint,
    allowSameOrigin: Boolean,
    allowsAnyHost: Boolean,
    hostsNormalized: Set<String>,
    hostsWithWildcard: Set<Pair<String, String>>,
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
    numberRegex: Regex
): Boolean {
    val normalizedOrigin = normalizeOrigin(origin, numberRegex)
    return allowsAnyHost || normalizedOrigin in hostsNormalized || hostsWithWildcard.any { (prefix, suffix) ->
        normalizedOrigin.startsWith(prefix) && normalizedOrigin.endsWith(suffix)
    }
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
    if (origin.isEmpty()) {
        return false
    }
    if (origin == "null") {
        return true
    }
    if ("%" in origin) {
        return false
    }

    val protoDelimiter = origin.indexOf("://")
    if (protoDelimiter <= 0) {
        return false
    }

    val protoValid = origin[0].isLetter() && origin.subSequence(0, protoDelimiter).all { ch ->
        ch.isLetter() || ch.isDigit() || ch == '-' || ch == '+' || ch == '.'
    }

    if (!protoValid) {
        return false
    }

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
        if (!origin[index].isDigit()) {
            return false
        }
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
