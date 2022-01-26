/*
 * Copyright 2014-2022 JetBrains s.r.o and contributors. Use of this source code is governed by the Apache 2.0 license.
 */

package io.ktor.server.plugins.forwardedheaders

import io.ktor.http.*
import io.ktor.server.application.*
import io.ktor.server.plugins.*
import io.ktor.util.*

/**
 * Configuration options for [XForwardedHeaders] plugin. Allows for defining how XForwarded-Headers
 * should behave.
 */
@KtorDsl
public class XForwardedHeadersConfig {
    /**
     * Host name X-header names. Default are `X-Forwarded-Server` and `X-Forwarded-Host`
     */
    public val hostHeaders: ArrayList<String> = arrayListOf(HttpHeaders.XForwardedHost, HttpHeaders.XForwardedServer)

    /**
     * Protocol X-header names. Default are `X-Forwarded-Proto` and `X-Forwarded-Protocol`
     */
    public val protoHeaders: MutableList<String> = mutableListOf(HttpHeaders.XForwardedProto, "X-Forwarded-Protocol")

    /**
     * `X-Forwarded-For` header names
     */
    public val forHeaders: MutableList<String> = mutableListOf(HttpHeaders.XForwardedFor)

    /**
     * HTTPS/TLS flag header names. Default are `X-Forwarded-SSL` and `Front-End-Https`
     */
    public val httpsFlagHeaders: MutableList<String> = mutableListOf("X-Forwarded-SSL", "Front-End-Https")

    /**
     * Names of headers used to identify the destination port. The default is `X-Forwarded-Port`
     */
    public val portHeaders: MutableList<String> = mutableListOf("X-Forwarded-Port")

    internal var xForwardedHeadersHandler: (MutableOriginConnectionPoint, XForwardedHeaderValues) -> Unit = { _, _ -> }

    init {
        useFirstProxy()
    }

    /**
     * Custom logic to extract the value from the X-Forward-* headers when multiple values are present.
     * You need to modify [MutableOriginConnectionPoint] based on headers from [XForwardedHeaderValues]
     */
    public fun extractEdgeProxy(block: (MutableOriginConnectionPoint, XForwardedHeaderValues) -> Unit) {
        xForwardedHeadersHandler = block
    }

    /**
     * Takes the first value from the X-Forward-* headers when multiple values are present
     */
    public fun useFirstProxy() {
        extractEdgeProxy { connectionPoint, headers ->
            setValues(connectionPoint, headers) { it.firstOrNull()?.trim() }
        }
    }

    /**
     * Takes the last value from the X-Forward-* headers when multiple values are present
     */
    public fun useLastProxy() {
        extractEdgeProxy { connectionPoint, headers ->
            setValues(connectionPoint, headers) { it.lastOrNull()?.trim() }
        }
    }

    /**
     * Takes the [proxiesCount]-before-last value from the X-Forward-* headers when multiple values are present
     */
    public fun skipLastProxies(proxiesCount: Int) {
        extractEdgeProxy { connectionPoint, headers ->
            setValues(connectionPoint, headers) { values ->
                values.getOrElse(values.size - proxiesCount - 1) { values.lastOrNull() }?.trim()
            }
        }
    }

    /**
     * Removes known [hosts] from the end of the list and takes the last value
     * from X-Forward-* headers when multiple values are present
     * */
    public fun skipKnownProxies(hosts: List<String>) {
        extractEdgeProxy { connectionPoint, headers ->
            val forValues = headers.forHeader?.split(',')

            var proxiesCount = 0
            while (
                hosts.lastIndex >= proxiesCount &&
                forValues != null && forValues.lastIndex >= proxiesCount &&
                hosts[hosts.size - proxiesCount - 1].trim() == forValues[forValues.size - proxiesCount - 1].trim()
            ) {
                proxiesCount++
            }
            setValues(connectionPoint, headers) { values ->
                values.getOrElse(values.size - proxiesCount - 1) { values.lastOrNull() }?.trim()
            }
        }
    }

    private fun setValues(
        connectionPoint: MutableOriginConnectionPoint,
        headers: XForwardedHeaderValues,
        extractValue: (List<String>) -> String?
    ) {
        val protoValues = headers.protoHeader?.split(',')
        val httpsFlagValues = headers.httpsFlagHeader?.split(',')
        val hostValues = headers.hostHeader?.split(',')
        val portValues = headers.portHeader?.split(',')
        val forValues = headers.forHeader?.split(',')

        protoValues?.let { values ->
            val scheme = extractValue(values) ?: return@let
            connectionPoint.scheme = scheme
            URLProtocol.byName[scheme]?.let { connectionPoint.port = it.defaultPort }
        }

        httpsFlagValues?.let { values ->
            val useHttps = extractValue(values).toBoolean()
            if (!useHttps) return@let

            connectionPoint.let { route ->
                route.scheme = "https"
                route.port = URLProtocol.HTTPS.defaultPort
            }
        }

        hostValues?.let { values ->
            val hostAndPort = extractValue(values) ?: return@let
            val host = hostAndPort.substringBefore(':')
            val port = hostAndPort.substringAfter(':', "")

            connectionPoint.host = host
            port.toIntOrNull()?.let {
                connectionPoint.port = it
            } ?: URLProtocol.byName[connectionPoint.scheme]?.let {
                connectionPoint.port = it.defaultPort
            }
        }

        portValues?.let { values ->
            val port = extractValue(values) ?: return@let
            connectionPoint.port = port.toInt()
        }

        forValues?.let { values ->
            val remoteHost = extractValue(values) ?: return@let
            if (remoteHost.isNotBlank()) {
                connectionPoint.remoteHost = remoteHost
            }
        }
    }

    private fun String?.toBoolean() = this == "yes" || this == "true" || this == "on"
}

/**
 * Values of X-Forward-* headers. Each property may contain multiple comma-separated values.
 */
public data class XForwardedHeaderValues(
    /**
     * Comma-separated list of values for [XForwardedHeadersConfig.protoHeaders] header
     */
    public val protoHeader: String?,
    /**
     * Comma-separated list of values for [XForwardedHeadersConfig.forHeaders] header
     */
    public val forHeader: String?,
    /**
     * Comma-separated list of values for [XForwardedHeadersConfig.hostHeaders] header
     */
    public val hostHeader: String?,
    /**
     * Comma-separated list of values for [XForwardedHeadersConfig.httpsFlagHeaders] header
     */
    public val httpsFlagHeader: String?,
    /**
     * Comma-separated list of values for [XForwardedHeadersConfig.portHeaders] header
     */
    public val portHeader: String?
)

/**
 * [XForwardedHeaders] plugin allows you to obtain information headers from the original request in reverse proxy
 * setups. For more information see https://datatracker.ietf.org/doc/html/rfc7239
 */
public val XForwardedHeaders: ApplicationPlugin<Application, XForwardedHeadersConfig, PluginInstance> =
    createApplicationPlugin("XForwardedHeaders", createConfiguration = { XForwardedHeadersConfig() }) {

        onCall { call ->
            val strategy = pluginConfig.xForwardedHeadersHandler
            val headers = XForwardedHeaderValues(
                protoHeader = pluginConfig.protoHeaders.firstNotNullOfOrNull { call.request.headers[it] },
                forHeader = pluginConfig.forHeaders.firstNotNullOfOrNull { call.request.headers[it] },
                hostHeader = pluginConfig.hostHeaders.firstNotNullOfOrNull { call.request.headers[it] },
                httpsFlagHeader = pluginConfig.httpsFlagHeaders.firstNotNullOfOrNull { call.request.headers[it] },
                portHeader = pluginConfig.portHeaders.firstNotNullOfOrNull { call.request.headers[it] },
            )
            strategy.invoke(call.mutableOriginConnectionPoint, headers)
        }
    }
