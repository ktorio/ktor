/*
 * Copyright 2014-2019 JetBrains s.r.o and contributors. Use of this source code is governed by the Apache 2.0 license.
 */

package io.ktor.features

import io.ktor.application.*
import io.ktor.http.*
import io.ktor.util.pipeline.*
import io.ktor.request.*
import io.ktor.response.*
import io.ktor.util.*
import java.net.*
import java.time.*
import java.util.*
import kotlin.collections.HashSet

/**
 * CORS feature. Please read http://ktor.io/servers/features/cors.html first before using it.
 */
class CORS(configuration: Configuration) {
    private val numberRegex = "[0-9]+".toRegex()

    /**
     * Allow requests from the same origin
     */
    val allowSameOrigin = configuration.allowSameOrigin

    /**
     * Allow requests from any origin
     */
    val allowsAnyHost = "*" in configuration.hosts

    /**
     * Allow to pass credentials
     */
    val allowCredentials = configuration.allowCredentials

    /**
     * All allowed headers to be sent including simple
     */
    val allHeaders = (configuration.headers + Configuration.CorsSimpleRequestHeaders).let { headers ->
        if (configuration.allowNonSimpleContentTypes) headers else headers.minus(HttpHeaders.ContentType)
    }

    /**
     * All allowed HTTP methods
     */
    val methods = HashSet<HttpMethod>(configuration.methods + Configuration.CorsDefaultMethods)

    /**
     * Set of all allowed headers
     */
    val allHeadersSet: Set<String> = allHeaders.map { it.toLowerCase() }.toSet()

    private val allowNonSimpleContentTypes: Boolean = configuration.allowNonSimpleContentTypes

    private val headersListHeaderValue =
        configuration.headers.filterNot { it in Configuration.CorsSimpleRequestHeaders }
            .let { if (allowNonSimpleContentTypes) it + HttpHeaders.ContentType else it }
            .sorted()
            .joinToString(", ")

    private val methodsListHeaderValue =
        methods.filterNot { it in Configuration.CorsDefaultMethods }
            .map { it.value }
            .sorted()
            .joinToString(", ")

    private val maxAgeHeaderValue = (configuration.maxAge.toMillis() / 1000).let { if (it > 0) it.toString() else null }
    private val exposedHeaders = when {
        configuration.exposedHeaders.isNotEmpty() -> configuration.exposedHeaders.sorted().joinToString(", ")
        else -> null
    }

    private val hostsNormalized = HashSet<String>(configuration.hosts.map { normalizeOrigin(it) })

    /**
     * Feature's call interceptor that does all the job. Usually there is no need to install it as it is done during
     * feature installation
     */
    suspend fun intercept(context: PipelineContext<Unit, ApplicationCall>) {
        val call = context.call

        if (!allowsAnyHost || allowCredentials) {
            call.corsVary()
        }

        val origin = call.request.headers.getAll(HttpHeaders.Origin)?.singleOrNull()
            ?.takeIf(this::isValidOrigin)
            ?: return

        if (allowSameOrigin && call.isSameOrigin(origin)) return

        if (!corsCheckOrigins(origin)) {
            context.respondCorsFailed()
            return
        }

        if (call.request.httpMethod == HttpMethod.Options) {
            call.respondPreflight(origin)
            // TODO: it shouldn't be here, because something else can respond to OPTIONS
            // But if noone else responds, we should respond with OK
            context.finish()
            return
        }

        if (!call.corsCheckCurrentMethod()) {
            context.respondCorsFailed()
            return
        }

        call.accessControlAllowOrigin(origin)
        call.accessControlAllowCredentials()

        if (exposedHeaders != null) {
            call.response.header(HttpHeaders.AccessControlExposeHeaders, exposedHeaders)
        }
    }

    private suspend fun ApplicationCall.respondPreflight(origin: String) {
        if (!corsCheckRequestMethod() || !corsCheckRequestHeaders()) {
            respond(HttpStatusCode.Forbidden)
            return
        }

        accessControlAllowOrigin(origin)
        accessControlAllowCredentials()
        if (methodsListHeaderValue.isNotEmpty()) {
            response.header(HttpHeaders.AccessControlAllowMethods, methodsListHeaderValue)
        }
        if (headersListHeaderValue.isNotEmpty()) {
            response.header(HttpHeaders.AccessControlAllowHeaders, headersListHeaderValue)
        }
        accessControlMaxAge()

        respond(HttpStatusCode.OK)
    }

    private fun ApplicationCall.accessControlAllowOrigin(origin: String) {
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

    private fun ApplicationCall.accessControlAllowCredentials() {
        if (allowCredentials) {
            response.header(HttpHeaders.AccessControlAllowCredentials, "true")
        }
    }

    private fun ApplicationCall.accessControlMaxAge() {
        if (maxAgeHeaderValue != null) {
            response.header(HttpHeaders.AccessControlMaxAge, maxAgeHeaderValue)
        }
    }

    private fun ApplicationCall.isSameOrigin(origin: String): Boolean {
        val requestOrigin = "${this.request.origin.scheme}://${this.request.origin.host}:${this.request.origin.port}"
        return normalizeOrigin(requestOrigin) == normalizeOrigin(origin)
    }

    private fun corsCheckOrigins(origin: String): Boolean {
        return allowsAnyHost || normalizeOrigin(origin) in hostsNormalized
    }

    private fun ApplicationCall.corsCheckRequestHeaders(): Boolean {
        val requestHeaders =
            request.headers.getAll(HttpHeaders.AccessControlRequestHeaders)?.flatMap { it.split(",") }?.map {
                it.trim().toLowerCase()
            } ?: emptyList()

        return requestHeaders.none { it !in allHeadersSet }
    }

    private fun ApplicationCall.corsCheckCurrentMethod(): Boolean {
        return request.httpMethod in methods
    }

    private fun ApplicationCall.corsCheckRequestMethod(): Boolean {
        val requestMethod = request.header(HttpHeaders.AccessControlRequestMethod)?.let { HttpMethod(it) }
        return requestMethod != null && !(requestMethod !in methods)
    }

    private suspend fun PipelineContext<Unit, ApplicationCall>.respondCorsFailed() {
        call.respond(HttpStatusCode.Forbidden)
        finish()
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

        return try {
            val url = URI(origin)

            !url.scheme.isNullOrEmpty()
        } catch (e: URISyntaxException) {
            false
        }
    }

    private fun normalizeOrigin(origin: String) =
        if (origin == "null" || origin == "*") origin else StringBuilder(origin.length).apply {
            append(origin)

            if (!origin.substringAfterLast(":", "").matches(numberRegex)) {
                val schema = origin.substringBefore(':')
                val port = when (schema) {
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

    /**
     * CORS feature configuration
     */
    class Configuration {
        companion object {
            /**
             * Default HTTP methods that are always allowed by CORS
             */
            val CorsDefaultMethods: Set<HttpMethod> = setOf(HttpMethod.Get, HttpMethod.Post, HttpMethod.Head)

            // https://www.w3.org/TR/cors/#simple-header
            /**
             * Default HTTP headers that are always allowed by CORS
             */
            @Deprecated("Use CorsSimpleRequestHeaders or CorsSimpleResponseHeaders instead")
            val CorsDefaultHeaders: Set<String> = TreeSet(String.CASE_INSENSITIVE_ORDER).apply {
                addAll(
                    listOf(
                        HttpHeaders.CacheControl,
                        HttpHeaders.ContentLanguage,
                        HttpHeaders.ContentType,
                        HttpHeaders.Expires,
                        HttpHeaders.LastModified,
                        HttpHeaders.Pragma
                    )
                )
            }

            /**
             * Default HTTP headers that are always allowed by CORS
             * (simple request headers according to https://www.w3.org/TR/cors/#simple-header )
             * Please note that `Content-Type` header simplicity depends on it's value.
             */
            val CorsSimpleRequestHeaders: Set<String> =
                Collections.unmodifiableSet(TreeSet(String.CASE_INSENSITIVE_ORDER).apply {
                    addAll(
                        listOf(
                            HttpHeaders.Accept,
                            HttpHeaders.AcceptLanguage,
                            HttpHeaders.ContentLanguage,
                            HttpHeaders.ContentType
                        )
                    )
                })

            /**
             * Default HTTP headers that are always allowed by CORS to be used in response
             * (simple request headers according to https://www.w3.org/TR/cors/#simple-header )
             */
            val CorsSimpleResponseHeaders: Set<String> =
                Collections.unmodifiableSet(TreeSet(String.CASE_INSENSITIVE_ORDER).apply {
                    addAll(
                        listOf(
                            HttpHeaders.CacheControl,
                            HttpHeaders.ContentLanguage,
                            HttpHeaders.ContentType,
                            HttpHeaders.Expires,
                            HttpHeaders.LastModified,
                            HttpHeaders.Pragma
                        )
                    )
                })

            /**
             * The allowed set of content types that are allowed by CORS without preflight check
             */
            val CorsSimpleContentTypes: Set<ContentType> = Collections.unmodifiableSet(
                setOf(
                    ContentType.Application.FormUrlEncoded,
                    ContentType.MultiPart.FormData,
                    ContentType.Text.Plain
                )
            )
        }

        /**
         * Allowed CORS hosts
         */
        val hosts = HashSet<String>()

        /**
         * Allowed CORS headers
         */
        val headers = TreeSet<String>(String.CASE_INSENSITIVE_ORDER)

        /**
         * Allowed HTTP methods
         */
        val methods = HashSet<HttpMethod>()

        /**
         * Exposed HTTP headers that could be accessed by a client
         */
        val exposedHeaders = TreeSet<String>(String.CASE_INSENSITIVE_ORDER)

        /**
         * Allow sending credentials
         */
        var allowCredentials = false

        /**
         * Max-Age for cached CORS options
         */
        var maxAge: Duration = Duration.ofDays(1)

        /**
         * Allow requests from the same origin
         */
        var allowSameOrigin: Boolean = true

        /**
         * Allow sending requests with non-simple content-types. The following content types are considered simple:
         * - `text/plain`
         * - `application/x-www-form-urlencoded`
         * - `multipart/form-data`
         */
        var allowNonSimpleContentTypes: Boolean = false

        /**
         * Allow requests from any host
         */
        fun anyHost() {
            hosts.add("*")
        }

        /**
         * Allow requests from the specified domains and schemes
         */
        fun host(host: String, schemes: List<String> = listOf("http"), subDomains: List<String> = emptyList()) {
            if (host == "*") {
                return anyHost()
            }
            if ("://" in host) {
                throw IllegalArgumentException("scheme should be specified as a separate parameter schemes")
            }

            for (schema in schemes) {
                hosts.add("$schema://$host")

                for (subDomain in subDomains) {
                    hosts.add("$schema://$subDomain.$host")
                }
            }
        }

        /**
         * Allow to expose [header]. It adds the [header] to `Access-Control-Expose-Headers` if it is not a
         * simple response header.
         */
        fun exposeHeader(header: String) {
            if (header !in CorsSimpleResponseHeaders) {
                exposedHeaders.add(header)
            }
        }

        /**
         * Allow to expose `X-Http-Method-Override` header
         */
        @Deprecated("Allow it in request headers instead", ReplaceWith("allowXHttpMethodOverride()"))
        fun exposeXHttpMethodOverride() {
            exposedHeaders.add(HttpHeaders.XHttpMethodOverride)
        }

        /**
         * Allow to send `X-Http-Method-Override` header
         */
        fun allowXHttpMethodOverride() {
            header(HttpHeaders.XHttpMethodOverride)
        }

        /**
         * Allow sending [header]
         */
        fun header(header: String) {
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
        fun method(method: HttpMethod) {
            if (method !in CorsDefaultMethods) {
                methods.add(method)
            }
        }
    }

    /**
     * Feature object for installation
     */
    companion object Feature : ApplicationFeature<ApplicationCallPipeline, Configuration, CORS> {
        override val key = AttributeKey<CORS>("CORS")
        override fun install(pipeline: ApplicationCallPipeline, configure: Configuration.() -> Unit): CORS {
            val cors = CORS(Configuration().apply(configure))
            pipeline.intercept(ApplicationCallPipeline.Features) { cors.intercept(this) }
            return cors
        }
    }
}





