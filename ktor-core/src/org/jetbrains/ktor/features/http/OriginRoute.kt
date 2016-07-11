package org.jetbrains.ktor.features.http

import org.jetbrains.ktor.application.*
import org.jetbrains.ktor.http.*
import org.jetbrains.ktor.util.*
import java.util.*

val ApplicationRequest.originRoute: RequestSocketRoute
    get() = object : RequestSocketRoute {
        private val forwarded by lazy { forwarded() }
        private val xForwardedHost by lazy { xForwardedHost() }

        override val scheme: String by lazy {
            forwarded?.firstOrNull()?.proto
                    ?: xForwardedProto()
                    ?: actualRoute.scheme
        }

        override val port: Int by lazy {
            forwarded?.firstOrNull()?.host?.port()
                    ?: xForwardedHost?.port()
                    ?: forwarded?.firstOrNull()?.proto?.let { it.port() }
                    ?: xForwardedProto()?.let { it.port() }
                    ?: actualRoute.port
        }

        override val host: String
            get() = forwarded?.firstOrNull()?.host?.substringBefore(":")
                    ?: xForwardedHost?.substringBefore(":")
                    ?: actualRoute.host

        override val remoteHost: String
            get() = forwarded?.firstOrNull()?.forParam
                    ?: xForwardedFor()?.split(",")?.first()?.trim()
                    ?: actualRoute.remoteHost

        private fun String.port() = substringAfterLast(":", "").let {
            if (it.isNotEmpty()) {
                it.toInt()
            } else {
                URLProtocol.byName[scheme]?.defaultPort
            }
        }
    }

private fun ApplicationRequest.xForwardedHost() = headers[HttpHeaders.XForwardedHost]
private fun ApplicationRequest.xForwardedProto() = headers[HttpHeaders.XForwardedProto]
private fun ApplicationRequest.xForwardedFor() = headers[HttpHeaders.XForwardedFor]

// See RFC 7239 https://tools.ietf.org/html/rfc7239
data class ForwardedHeaderValue(val host: String?, val by: String?, val forParam: String?, val proto: String?, val others: Map<String, String>)

fun ApplicationRequest.forwarded() = headers.getAll(HttpHeaders.Forwarded)?.flatMap { parseHeaderValue(";" + it) }?.mapNotNull { parseForwardedValue(it) }
private fun parseForwardedValue(value: HeaderValue): ForwardedHeaderValue? {
    val map = value.params.associateByTo(HashMap<String, String>(), { it.name }, { it.value })

    return ForwardedHeaderValue(map.remove("host"), map.remove("by"), map.remove("for"), map.remove("proto"), map)
}

