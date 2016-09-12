package org.jetbrains.ktor.features.http

import org.jetbrains.ktor.application.*
import org.jetbrains.ktor.features.*
import org.jetbrains.ktor.http.*
import org.jetbrains.ktor.request.*
import org.jetbrains.ktor.response.*
import org.jetbrains.ktor.util.*
import java.net.*
import java.time.*
import java.util.*

class CORS(configuration: Configuration) {
    private val numberRegex = "[0-9]+".toRegex()

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

    fun intercept(call: ApplicationCall) {
        val origin = call.request.headers.getAll(HttpHeaders.Origin)?.singleOrNull()
        if (origin != null && isValidOrigin(origin)) {
            call.corsCheckOrigins(origin)

            if (call.request.httpMethod == HttpMethod.Options) {
                call.preFlight(call, origin)
            }

            call.corsCheckCurrentMethod()
            call.accessControlAllowOrigin(origin)
            call.accessControlAllowCredentials()

            if (exposedHeaders != null) {
                call.response.header(HttpHeaders.AccessControlExposeHeaders, exposedHeaders)
            }
        }
    }

    private fun ApplicationCall.preFlight(call: ApplicationCall, origin: String): Nothing {
        corsCheckRequestMethod()
        corsCheckRequestHeaders()

        call.accessControlAllowOrigin(origin)
        call.response.header(HttpHeaders.AccessControlAllowMethods, methodsListHeaderValue)
        call.response.header(HttpHeaders.AccessControlAllowHeaders, headersListHeaderValue)
        call.accessControlAllowCredentials()
        call.accessControlMaxAge()

        call.respond(HttpStatusCode.OK)
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

    private fun ApplicationCall.corsCheckOrigins(origin: String) {
        if (!allowsAnyHost && normalizeOrigin(origin) !in hostsNormalized) {
            corsFail()
        }
    }

    private fun ApplicationCall.corsCheckRequestHeaders() {
        val requestHeaders = request.headers.getAll(HttpHeaders.AccessControlRequestHeaders)?.flatMap { it.split(",") }?.map { it.trim().toLowerCase() } ?: emptyList()

        if (requestHeaders.any { it !in headers }) {
            corsFail()
        }
    }

    private fun ApplicationCall.corsCheckCurrentMethod() {
        val requestMethod = request.httpMethod

        if (requestMethod !in methods) {
            corsFail()
        }
    }

    private fun ApplicationCall.corsCheckRequestMethod() {
        val requestMethod = request.header(HttpHeaders.AccessControlRequestMethod)?.let { HttpMethod(it) }

        if (requestMethod == null || (requestMethod !in methods)) {
            corsFail()
        }
    }

    private fun ApplicationCall.corsFail(): Nothing = respond(HttpStatusCode.Forbidden)

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
            pipeline.intercept(ApplicationCallPipeline.Infrastructure) { cors.intercept(it) }
            return cors
        }
    }
}





