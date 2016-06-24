package org.jetbrains.ktor.http

import org.jetbrains.ktor.application.*
import org.jetbrains.ktor.pipeline.*
import java.net.*
import java.time.*
import java.util.*

class CORSBuilder {
    val hosts = TreeSet<String>(String.CASE_INSENSITIVE_ORDER)
    val headers = TreeSet<String>(String.CASE_INSENSITIVE_ORDER)
    val methods = HashSet<HttpMethod>()
    val exposedHeaders = TreeSet<String>(String.CASE_INSENSITIVE_ORDER)

    var allowCredentials = false

    var maxAge: Duration = Duration.ofDays(1)
}

fun CORSBuilder.anyHost() {
    hosts.add("*")
}

fun CORSBuilder.host(host: String, https: Boolean = false, www: Boolean = false) {
    if (host == "*") {
        return anyHost()
    }
    val schemeIndex = host.indexOf("://")
    if (schemeIndex != -1) {
        return host(host.drop(schemeIndex + 3), https, www)
    }

    hosts.add("http://$host")
    if (https) {
        hosts.add("https://$host")
    }

    if (www) {
        if (host.startsWith("www.")) {
            host(host.removePrefix("www."), https, false)
        } else {
            host("www.$host", https, false)
        }
    }
}

fun CORSBuilder.exposeHeader(header: String) {
    exposedHeaders.add(header)
}
fun CORSBuilder.header(header: String) {
    if (header !in CorsDefaultHeaders) {
        headers.add(header)
    }
}
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
    val builder = CORSBuilder()

    block(builder)

    intercept(ApplicationCallPipeline.Infrastructure) { call ->
        val origin = call.request.header(HttpHeaders.Origin)
        if (origin != null && isValidOrigin(origin) && call.request.headers.getAll(HttpHeaders.Origin)?.size == 1) {
            corsCheckOrigins(origin, builder)

            if (call.request.httpMethod == HttpMethod.Options) {
                preFlight(call, builder, origin)
            }

            corsCheckCurrentMethod(builder)
            call.accessControlAllowOrigin(builder, origin)
            call.accessControlAllowCredentials(builder)

            if (builder.exposedHeaders.isNotEmpty()) {
                call.response.header(HttpHeaders.AccessControlExposeHeaders, builder.exposedHeaders.joinToString(", "))
            }
        }
    }
}

private fun PipelineContext<ApplicationCall>.preFlight(call: ApplicationCall, builder: CORSBuilder, origin: String): Nothing {
    corsCheckRequestMethod(builder)
    corsCheckRequestHeaders(builder)

    call.accessControlAllowOrigin(builder, origin)
    call.response.header(HttpHeaders.AccessControlAllowMethods, (builder.methods + CorsDefaultMethods).joinToString(", ") { it.value })
    call.response.header(HttpHeaders.AccessControlAllowHeaders, (builder.headers + CorsDefaultHeaders).joinToString(", "))
    call.accessControlAllowCredentials(builder)
    call.accessControlMaxAge(builder)

    call.respond(HttpStatusCode.OK)
}

private fun ApplicationCall.accessControlAllowOrigin(builder: CORSBuilder, origin: String) {
    if ("*" in builder.hosts && !builder.allowCredentials) {
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

private fun ApplicationCall.accessControlAllowCredentials(builder: CORSBuilder) {
    if (builder.allowCredentials) {
        response.header(HttpHeaders.AccessControlAllowCredentials, "true")
    }
}

private fun ApplicationCall.accessControlMaxAge(builder: CORSBuilder) {
    val maxAge = builder.maxAge.toMillis() / 1000
    if (maxAge > 0) {
        response.header(HttpHeaders.AccessControlMaxAge, maxAge)
    }
}

private fun PipelineContext<ApplicationCall>.corsCheckOrigins(origin: String, builder: CORSBuilder) {
    if ("*" !in builder.hosts && origin !in builder.hosts) {
        corsFail()
    }
}

private fun PipelineContext<ApplicationCall>.corsCheckRequestHeaders(builder: CORSBuilder) {
    val requestHeaders = call.request.headers.getAll(HttpHeaders.AccessControlRequestHeaders)?.flatMap { it.split(",") }?.map { it.trim() } ?: emptyList()

    if (requestHeaders.any { it !in CorsDefaultHeaders && it !in builder.headers }) {
        corsFail()
    }
}

private fun PipelineContext<ApplicationCall>.corsCheckCurrentMethod(builder: CORSBuilder) {
    val requestMethod = call.request.httpMethod

    if (requestMethod !in CorsDefaultMethods && requestMethod !in builder.methods ) {
        corsFail()
    }
}

private fun PipelineContext<ApplicationCall>.corsCheckRequestMethod(builder: CORSBuilder) {
    val requestMethod = call.request.header(HttpHeaders.AccessControlRequestMethod)?.let { HttpMethod(it) }

    if (requestMethod == null || (requestMethod !in CorsDefaultMethods && requestMethod !in builder.methods)) {
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
