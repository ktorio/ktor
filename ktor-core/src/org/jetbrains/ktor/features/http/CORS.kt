package org.jetbrains.ktor.features.http

import org.jetbrains.ktor.application.*
import org.jetbrains.ktor.http.*
import org.jetbrains.ktor.pipeline.*
import org.jetbrains.ktor.request.*
import org.jetbrains.ktor.response.*
import java.net.*
import java.time.*
import java.util.*

class CORSBuilder {
    val hosts = HashSet<String>()
    val headers = TreeSet<String>(String.CASE_INSENSITIVE_ORDER)
    val methods = HashSet<HttpMethod>()
    val exposedHeaders = TreeSet<String>(String.CASE_INSENSITIVE_ORDER)

    var allowCredentials = false

    var maxAge: Duration = Duration.ofDays(1)
}

private class CORS(builder: CORSBuilder) {
    val allowAnyHost = "*" in builder.hosts
    val hostsNormalized = HashSet<String>(builder.hosts.map { normalizeOrigin(it) })

    val allHeaders = builder.headers + CorsDefaultHeaders
    val headers = allHeaders.map { it.toLowerCase() }.toSet()
    val headersListHeaderValue = allHeaders.sorted().joinToString(", ")

    val methods = HashSet<HttpMethod>(builder.methods + CorsDefaultMethods)
    val methodsListHeaderValue = methods.map { it.value }.sorted().joinToString(", ")

    val allowCredentials = builder.allowCredentials
    val maxAgeHeaderValue = (builder.maxAge.toMillis() / 1000).let { if (it > 0) it.toString() else null }
    val exposedHeaders = if (builder.exposedHeaders.isNotEmpty()) builder.exposedHeaders.sorted().joinToString(", ") else null
}

fun CORSBuilder.anyHost() {
    hosts.add("*")
}

fun CORSBuilder.host(host: String, schemes: List<String> = listOf("http"), subDomains: List<String> = emptyList()) {
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

fun CORSBuilder.exposeHeader(header: String) {
    exposedHeaders.add(header)
}
fun CORSBuilder.exposeXHttpMethodOverride() {
    exposedHeaders.add(HttpHeaders.XHttpMethodOverride)
}

fun CORSBuilder.header(header: String) {
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
fun CORSBuilder.method(method: HttpMethod) {
    if (method !in CorsDefaultMethods) {
        methods.add(method)
    }
}

private val CorsDefaultMethods = setOf(HttpMethod.Get, HttpMethod.Post, HttpMethod.Head)

// https://www.w3.org/TR/cors/#simple-header
private val CorsDefaultHeaders: Set<String> = TreeSet(String.CASE_INSENSITIVE_ORDER).apply {
    addAll(listOf(
            HttpHeaders.CacheControl,
            HttpHeaders.ContentLanguage,
            HttpHeaders.ContentType,
            HttpHeaders.Expires,
            HttpHeaders.LastModified,
            HttpHeaders.Pragma
    ))
}

fun Pipeline<ApplicationCall>.CORS(block: CORSBuilder.() -> Unit) {
    val config = CORS(CORSBuilder().apply(block))

    intercept(ApplicationCallPipeline.Infrastructure) { call ->
        val origin = call.request.headers.getAll(HttpHeaders.Origin)?.singleOrNull()
        if (origin != null && isValidOrigin(origin)) {
            corsCheckOrigins(origin, config)

            if (call.request.httpMethod == HttpMethod.Options) {
                preFlight(call, config, origin)
            }

            corsCheckCurrentMethod(config)
            call.accessControlAllowOrigin(config, origin)
            call.accessControlAllowCredentials(config)

            if (config.exposedHeaders != null) {
                call.response.header(HttpHeaders.AccessControlExposeHeaders, config.exposedHeaders)
            }
        }
    }
}

private fun PipelineContext<ApplicationCall>.preFlight(call: ApplicationCall, config: CORS, origin: String): Nothing {
    corsCheckRequestMethod(config)
    corsCheckRequestHeaders(config)

    call.accessControlAllowOrigin(config, origin)
    call.response.header(HttpHeaders.AccessControlAllowMethods, config.methodsListHeaderValue)
    call.response.header(HttpHeaders.AccessControlAllowHeaders, config.headersListHeaderValue)
    call.accessControlAllowCredentials(config)
    call.accessControlMaxAge(config)

    call.respond(HttpStatusCode.OK)
}

private fun ApplicationCall.accessControlAllowOrigin(config: CORS, origin: String) {
    if (config.allowAnyHost && !config.allowCredentials) {
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

private fun ApplicationCall.accessControlAllowCredentials(config: CORS) {
    if (config.allowCredentials) {
        response.header(HttpHeaders.AccessControlAllowCredentials, "true")
    }
}

private fun ApplicationCall.accessControlMaxAge(config: CORS) {
    if (config.maxAgeHeaderValue != null) {
        response.header(HttpHeaders.AccessControlMaxAge, config.maxAgeHeaderValue)
    }
}

private fun PipelineContext<ApplicationCall>.corsCheckOrigins(origin: String, config: CORS) {
    if (!config.allowAnyHost && normalizeOrigin(origin) !in config.hostsNormalized) {
        corsFail()
    }
}

private fun PipelineContext<ApplicationCall>.corsCheckRequestHeaders(config: CORS) {
    val requestHeaders = call.request.headers.getAll(HttpHeaders.AccessControlRequestHeaders)?.flatMap { it.split(",") }?.map { it.trim().toLowerCase() } ?: emptyList()

    if (requestHeaders.any { it !in config.headers }) {
        corsFail()
    }
}

private fun PipelineContext<ApplicationCall>.corsCheckCurrentMethod(config: CORS) {
    val requestMethod = call.request.httpMethod

    if (requestMethod !in config.methods) {
        corsFail()
    }
}

private fun PipelineContext<ApplicationCall>.corsCheckRequestMethod(config: CORS) {
    val requestMethod = call.request.header(HttpHeaders.AccessControlRequestMethod)?.let { HttpMethod(it) }

    if (requestMethod == null || (requestMethod !in config.methods)) {
        corsFail()
    }
}

private fun PipelineContext<ApplicationCall>.corsFail(): Nothing {
    subject.respond(HttpStatusCode.Forbidden)
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

private val numberRegex = "[0-9]+".toRegex()
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