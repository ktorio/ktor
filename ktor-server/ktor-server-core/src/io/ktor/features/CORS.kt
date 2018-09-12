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

class CORS(configuration: Configuration) {
    private val numberRegex = "[0-9]+".toRegex()

    val allowSameOrigin = configuration.allowSameOrigin
    val allowsAnyHost = "*" in configuration.hosts
    val allowCredentials = configuration.allowCredentials
    val allHeaders = configuration.headers + Configuration.CorsDefaultHeaders

    val methods = HashSet<HttpMethod>(configuration.methods + Configuration.CorsDefaultMethods)
    val headers = allHeaders.map { it.toLowerCase() }.toSet()

    private val headersListHeaderValue = allHeaders.sorted().joinToString(", ")
    private val methodsListHeaderValue = methods.map { it.value }.sorted().joinToString(", ")
    private val maxAgeHeaderValue = (configuration.maxAge.toMillis() / 1000).let { if (it > 0) it.toString() else null }
    private val exposedHeaders = if (configuration.exposedHeaders.isNotEmpty()) configuration.exposedHeaders.sorted().joinToString(", ") else null
    private val hostsNormalized = HashSet<String>(configuration.hosts.map { normalizeOrigin(it) })

    suspend fun intercept(context: PipelineContext<Unit, ApplicationCall>) {
        val call = context.call
        val origin = call.request.headers.getAll(HttpHeaders.Origin)?.singleOrNull()
                ?.takeIf(this::isValidOrigin)
                ?: return

        if (allowSameOrigin && call.isSameOrigin(origin)) return

        if (!call.corsCheckOrigins(origin)) {
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
        response.header(HttpHeaders.AccessControlAllowMethods, methodsListHeaderValue)
        response.header(HttpHeaders.AccessControlAllowHeaders, headersListHeaderValue)
        accessControlMaxAge()
        respond(HttpStatusCode.OK)

    }

    private fun ApplicationCall.accessControlAllowOrigin(origin: String) {
        if (allowsAnyHost && !allowCredentials) {
            response.header(HttpHeaders.AccessControlAllowOrigin, "*")
        } else {
            response.header(HttpHeaders.AccessControlAllowOrigin, origin)
            corsVary()
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

    private fun ApplicationCall.corsCheckOrigins(origin: String): Boolean {
        return allowsAnyHost || normalizeOrigin(origin) in hostsNormalized
    }

    private fun ApplicationCall.corsCheckRequestHeaders(): Boolean {
        val requestHeaders = request.headers.getAll(HttpHeaders.AccessControlRequestHeaders)?.flatMap { it.split(",") }?.map { it.trim().toLowerCase() } ?: emptyList()

        return !requestHeaders.any { it !in headers }
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

    private fun normalizeOrigin(origin: String) = if (origin == "null" || origin == "*") origin else StringBuilder(origin.length).apply {
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

    class Configuration {
        companion object {
            val CorsDefaultMethods = setOf(HttpMethod.Get, HttpMethod.Post, HttpMethod.Head)

            // https://www.w3.org/TR/cors/#simple-header
            val CorsDefaultHeaders: Set<String> = TreeSet(String.CASE_INSENSITIVE_ORDER).apply {
                addAll(listOf(
                        HttpHeaders.CacheControl,
                        HttpHeaders.ContentLanguage,
                        HttpHeaders.ContentType,
                        HttpHeaders.Expires,
                        HttpHeaders.LastModified,
                        HttpHeaders.Pragma
                ))
            }
        }

        val hosts = HashSet<String>()
        val headers = TreeSet<String>(String.CASE_INSENSITIVE_ORDER)
        val methods = HashSet<HttpMethod>()
        val exposedHeaders = TreeSet<String>(String.CASE_INSENSITIVE_ORDER)

        var allowCredentials = false

        var maxAge: Duration = Duration.ofDays(1)
        var allowSameOrigin: Boolean = true

        fun anyHost() {
            hosts.add("*")
        }

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

        fun exposeHeader(header: String) {
            exposedHeaders.add(header)
        }

        fun exposeXHttpMethodOverride() {
            exposedHeaders.add(HttpHeaders.XHttpMethodOverride)
        }

        fun header(header: String) {
            if (header !in CorsDefaultHeaders) {
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

    companion object Feature : ApplicationFeature<ApplicationCallPipeline, Configuration, CORS> {
        override val key = AttributeKey<CORS>("CORS")
        override fun install(pipeline: ApplicationCallPipeline, configure: Configuration.() -> Unit): CORS {
            val cors = CORS(Configuration().apply(configure))
            pipeline.intercept(ApplicationCallPipeline.Features) { cors.intercept(this) }
            return cors
        }
    }
}





