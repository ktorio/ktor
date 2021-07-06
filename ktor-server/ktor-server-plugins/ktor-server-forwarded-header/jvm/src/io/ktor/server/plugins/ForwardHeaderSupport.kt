/*
 * Copyright 2014-2021 JetBrains s.r.o and contributors. Use of this source code is governed by the Apache 2.0 license.
 */

package io.ktor.server.plugins

import io.ktor.http.*
import io.ktor.server.application.*
import io.ktor.server.request.*
import io.ktor.util.*

/**
 * `X-Forwarded-*` headers support
 * See http://ktor.io/servers/features/forward-headers.html for details
 */
public object XForwardedHeaderSupport :
    ApplicationPlugin<ApplicationCallPipeline, XForwardedHeaderSupport.Config, XForwardedHeaderSupport.Config> {

    override val key: AttributeKey<Config> = AttributeKey("XForwardedHeaderSupport")

    override fun install(pipeline: ApplicationCallPipeline, configure: Config.() -> Unit): Config {
        val config = Config()
        configure(config)

        pipeline.intercept(ApplicationCallPipeline.Plugins) {
            call.forEachHeader(config.protoHeaders) { value ->
                call.mutableOriginConnectionPoint.let { route ->
                    route.scheme = value
                    URLProtocol.byName[value]?.let {
                        route.port = it.defaultPort
                    }
                }
            }

            call.forEachHeader(config.httpsFlagHeaders) { value ->
                if (!value.toBoolean()) return@forEachHeader

                call.mutableOriginConnectionPoint.let { route ->
                    route.scheme = "https"
                    URLProtocol.byName[route.scheme]?.let {
                        route.port = it.defaultPort
                    }
                }
            }

            call.forEachHeader(config.hostHeaders) { value ->
                val host = value.substringBefore(':')
                val port = value.substringAfter(':', "")

                call.mutableOriginConnectionPoint.let { route ->
                    route.host = host
                    port.toIntOrNull()?.let {
                        route.port = it
                    } ?: URLProtocol.byName[route.scheme]?.let {
                        route.port = it.defaultPort
                    }
                }
            }

            call.forEachHeader(config.portHeaders) { value ->
                val port = value.split(",").first().trim()
                call.mutableOriginConnectionPoint.port = port.toInt()
            }

            call.forEachHeader(config.forHeaders) { xForwardedFor ->
                val remoteHost = xForwardedFor.split(",").first().trim()
                if (remoteHost.isNotBlank()) {
                    call.mutableOriginConnectionPoint.remoteHost = remoteHost
                }
            }
        }

        return config
    }

    private fun String.toBoolean() = this == "yes" || this == "true" || this == "on"

    /**
     * [XForwardedHeaderSupport] plugin's configuration
     */
    @Suppress("PublicApiImplicitType")
    public class Config {
        /**
         * Host name X-header names. Default are `X-Forwarded-Server` and `X-Forwarded-Host`
         */
        public val hostHeaders: ArrayList<String> =
            arrayListOf(HttpHeaders.XForwardedHost, HttpHeaders.XForwardedServer)

        /**
         * Protocol X-header names. Default are `X-Forwarded-Proto` and `X-Forwarded-Protocol`
         */
        public val protoHeaders: ArrayList<String> = arrayListOf(HttpHeaders.XForwardedProto, "X-Forwarded-Protocol")

        /**
         * `X-Forwarded-For` header names
         */
        public val forHeaders: ArrayList<String> = arrayListOf(HttpHeaders.XForwardedFor)

        /**
         * HTTPS/TLS flag header names. Default are `X-Forwarded-SSL` and `Front-End-Https`
         */
        public val httpsFlagHeaders: ArrayList<String> = arrayListOf("X-Forwarded-SSL", "Front-End-Https")

        /**
         * Port X-header names. Default is `X-Forwarded-Port`
         */
        @PublicAPICandidate("2.0.0")
        internal val portHeaders: ArrayList<String> = arrayListOf("X-Forwarded-Port")
    }
}

/**
 * Forwarded header support. See RFC 7239 https://tools.ietf.org/html/rfc7239
 */
@Suppress("MemberVisibilityCanBePrivate")
public object ForwardedHeaderSupport : ApplicationPlugin<ApplicationCallPipeline, Unit, Unit> {
    /**
     * A key for application call attribute that is used to cache parsed header values
     */
    public val ForwardedParsedKey: AttributeKey<List<ForwardedHeaderValue>> = AttributeKey("ForwardedParsedKey")

    override val key: AttributeKey<Unit> = AttributeKey("ForwardedHeaderSupport")

    override fun install(pipeline: ApplicationCallPipeline, configure: Unit.() -> Unit) {
        configure(Unit)

        pipeline.intercept(ApplicationCallPipeline.Plugins) {
            val forwarded = call.request.forwarded() ?: return@intercept
            call.attributes.put(ForwardedParsedKey, forwarded)
            val firstForward = forwarded.firstOrNull() ?: return@intercept

            if (firstForward.proto != null) {
                call.mutableOriginConnectionPoint.let { route ->
                    val proto: String = firstForward.proto
                    route.scheme = proto
                    URLProtocol.byName[proto]?.let { p ->
                        route.port = p.defaultPort
                    }
                }
            }

            if (firstForward.forParam != null) {
                val remoteHost = firstForward.forParam.split(",").first().trim()
                if (remoteHost.isNotBlank()) {
                    call.mutableOriginConnectionPoint.remoteHost = remoteHost
                }
            }

            if (firstForward.host == null) return@intercept

            val host = firstForward.host.substringBefore(':')
            val port = firstForward.host.substringAfter(':', "")

            call.mutableOriginConnectionPoint.let { route ->
                route.host = host
                port.toIntOrNull()?.let { route.port = it } ?: URLProtocol.byName[route.scheme]?.let {
                    route.port = it.defaultPort
                }
            }
        }
    }

    /**
     * Parsed forwarded header value. All fields are optional as proxy could provide different fields
     * @property host field value (optional)
     * @property by field value (optional)
     * @property forParam field value (optional)
     * @property proto field value (optional)
     * @property others contains custom field values passed by proxy
     */
    public data class ForwardedHeaderValue(
        val host: String?,
        val by: String?,
        val forParam: String?,
        val proto: String?,
        val others: Map<String, String>
    )

    // do we need it public?
    private fun ApplicationRequest.forwarded() =
        headers.getAll(HttpHeaders.Forwarded)?.flatMap { parseHeaderValue(";$it") }?.map {
            parseForwardedValue(it)
        }

    private fun parseForwardedValue(value: HeaderValue): ForwardedHeaderValue {
        val map = value.params.associateByTo(HashMap(), { it.name }, { it.value })

        return ForwardedHeaderValue(map.remove("host"), map.remove("by"), map.remove("for"), map.remove("proto"), map)
    }
}

private inline fun ApplicationCall.forEachHeader(headers: List<String>, block: (String) -> Unit) {
    for (name in headers) {
        val value = request.header(name)
        if (value != null) {
            block(value)
        }
    }
}

private class AssignableWithDelegate<T : Any>(val property: () -> T) {
    private var assigned: T? = null

    @Suppress("UNCHECKED_CAST")
    operator fun getValue(thisRef: Any, property: KProperty<*>): T = assigned ?: property()

    operator fun setValue(thisRef: Any, property: KProperty<*>, value: T) {
        assigned = value
    }
}
