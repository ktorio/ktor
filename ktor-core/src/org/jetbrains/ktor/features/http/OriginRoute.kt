package org.jetbrains.ktor.features.http

import org.jetbrains.ktor.application.*
import org.jetbrains.ktor.features.*
import org.jetbrains.ktor.http.*
import org.jetbrains.ktor.request.*
import org.jetbrains.ktor.util.*
import java.util.*
import kotlin.properties.*
import kotlin.reflect.*

val MutableOriginRouteKey = AttributeKey<MutableOriginRoute>("originRoute")

class MutableOriginRoute(delegate: RequestSocketRoute) : RequestSocketRoute {
    override var version by AssignableWithDelegate<RequestSocketRoute, String>(delegate)
    override var uri by AssignableWithDelegate<RequestSocketRoute, String>(delegate)
    override var method by AssignableWithDelegate<RequestSocketRoute, HttpMethod>(delegate)
    override var scheme by AssignableWithDelegate<RequestSocketRoute, String>(delegate)
    override var host by AssignableWithDelegate<RequestSocketRoute, String>(delegate)
    override var port by AssignableWithDelegate<RequestSocketRoute, Int>(delegate)
    override var remoteHost by AssignableWithDelegate<RequestSocketRoute, String>(delegate)
}

object XForwardedHeadersSupport : ApplicationFeature<ApplicationCallPipeline, XForwardedHeadersSupport.Config> {

    override val key = AttributeKey<Config>("XForwardedHeadersSupport")

    override fun install(pipeline: ApplicationCallPipeline, configure: Config.() -> Unit): Config {
        val config = Config()
        configure(config)

        pipeline.intercept(ApplicationCallPipeline.Infrastructure) { call ->
            call.forEachHeader(config.protoHeaders) { value ->
                call.mutableOriginRoute.let { route ->
                    route.scheme = value
                    URLProtocol.byName[value]?.let {
                        route.port = it.defaultPort
                    }
                }
            }

            call.forEachHeader(config.httpsFlagHeaders) { value ->
                if (value.toBoolean()) {
                    call.mutableOriginRoute.let { route ->
                        route.scheme = "https"
                        URLProtocol.byName[route.scheme]?.let {
                            route.port = it.defaultPort
                        }
                    }
                }
            }

            call.forEachHeader(config.hostHeaders) { value ->
                val host = value.substringBefore(':')
                val port = value.substringAfter(':', "")

                call.mutableOriginRoute.let { route ->
                    route.host = host
                    port.tryParseInt()?.let {
                        route.port = it
                    } ?: URLProtocol.byName[route.scheme]?.let {
                        route.port = it.defaultPort
                    }
                }
            }

            call.forEachHeader(config.forHeaders) { xForwardedFor ->
                val remoteHost = xForwardedFor.split(",").first().trim()
                if (remoteHost.isNotBlank()) {
                    call.mutableOriginRoute.remoteHost = remoteHost
                }
            }
        }

        return config
    }

    private fun String.toBoolean() = this == "yes" || this == "true" || this == "on"

    class Config {
        val hostHeaders = arrayListOf(HttpHeaders.XForwardedHost, HttpHeaders.XForwardedServer)
        val protoHeaders = arrayListOf(HttpHeaders.XForwardedProto, "X-Forwarded-Protocol")
        val forHeaders = arrayListOf(HttpHeaders.XForwardedFor)
        val httpsFlagHeaders = arrayListOf("X-Forwarded-SSL", "Front-End-Https")
    }
}

/**
 * Forwarded header support. See RFC 7239 https://tools.ietf.org/html/rfc7239
 */
object ForwardedHeaderSupport : ApplicationFeature<ApplicationCallPipeline, Unit> {
    val ForwardedParsedKey = AttributeKey<List<ForwardedHeaderValue>>("ForwardedParsedKey")
    override val key = AttributeKey<Unit>("ForwardedHeaderSupport")

    override fun install(pipeline: ApplicationCallPipeline, configure: Unit.() -> Unit) {
        configure(Unit)

        pipeline.intercept(ApplicationCallPipeline.Infrastructure) { call ->
            val forwarded = call.request.forwarded()
            if (forwarded != null) {
                call.attributes.put(ForwardedParsedKey, forwarded)
                val firstForward = forwarded.firstOrNull()

                if (firstForward != null) {
                    if (firstForward.proto != null) {
                        call.mutableOriginRoute.let { route ->
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
                            call.mutableOriginRoute.remoteHost = remoteHost
                        }
                    }
                    if (firstForward.host != null) {
                        val host = firstForward.host.substringBefore(':')
                        val port = firstForward.host.substringAfter(':', "")

                        call.mutableOriginRoute.let { route ->
                            route.host = host
                            port.tryParseInt()?.let { route.port = it } ?: URLProtocol.byName[route.scheme]?.let {
                                route.port = it.defaultPort
                            }
                        }
                    }
                }
            }
        }
    }

    data class ForwardedHeaderValue(val host: String?, val by: String?, val forParam: String?, val proto: String?, val others: Map<String, String>)

    // do we need it public?
    private fun ApplicationRequest.forwarded() = headers.getAll(HttpHeaders.Forwarded)?.flatMap { parseHeaderValue(";" + it) }?.mapNotNull { parseForwardedValue(it) }
    private fun parseForwardedValue(value: HeaderValue): ForwardedHeaderValue? {
        val map = value.params.associateByTo(HashMap<String, String>(), { it.name }, { it.value })

        return ForwardedHeaderValue(map.remove("host"), map.remove("by"), map.remove("for"), map.remove("proto"), map)
    }
}

internal val ApplicationCall.mutableOriginRoute: MutableOriginRoute
    get() = attributes.computeIfAbsent(MutableOriginRouteKey) { MutableOriginRoute(request.localRoute) }

val ApplicationRequest.originRoute: RequestSocketRoute
    get() = call.attributes.getOrNull(MutableOriginRouteKey) ?: localRoute

private inline fun ApplicationCall.forEachHeader(headers: List<String>, block: (String) -> Unit) {
    for (name in headers) {
        val value = request.header(name)
        if (value != null) {
            block(value)
        }
    }
}

private class AssignableWithDelegate<D : Any, T : Any>(val delegate: D) : ReadWriteProperty<D, T> {
    private var assigned: T? = null

    @Suppress("UNCHECKED_CAST")
    override fun getValue(thisRef: D, property: KProperty<*>): T = assigned ?: (delegate.javaClass.kotlin.declaredMemberProperties.first { it.name == property.name }.get(delegate) as T)

    override fun setValue(thisRef: D, property: KProperty<*>, value: T) {
        assigned = value
    }
}

private fun String.tryParseInt() = try { if (isNotEmpty()) toInt() else null } catch (nfe: NumberFormatException) { null }