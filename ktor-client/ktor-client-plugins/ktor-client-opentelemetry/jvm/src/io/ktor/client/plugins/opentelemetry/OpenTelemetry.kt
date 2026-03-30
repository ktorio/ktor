/*
 * Copyright 2014-2025 JetBrains s.r.o and contributors. Use of this source code is governed by the Apache 2.0 license.
 */

package io.ktor.client.plugins.opentelemetry

import io.ktor.client.plugins.*
import io.ktor.client.plugins.api.*
import io.ktor.client.request.*
import io.ktor.util.*
import io.ktor.utils.io.*
import io.opentelemetry.api.metrics.*
import io.opentelemetry.api.trace.*
import io.opentelemetry.context.Context
import io.opentelemetry.context.propagation.*
import io.opentelemetry.api.OpenTelemetry as OpenTelemetryApi
import io.opentelemetry.api.common.AttributeKey as OtelAttributeKey
import io.opentelemetry.api.common.Attributes as OtelAttributes

/**
 * Configuration for the client-side [OpenTelemetry] plugin.
 *
 * Supports two configuration styles:
 * - Set [openTelemetry] to provide an [OpenTelemetryApi] instance with all components configured.
 * - Set individual [tracerProvider], [meterProvider], and [propagators] properties to override
 *   specific components.
 *
 * [Report a problem](https://ktor.io/feedback/?fqname=io.ktor.client.plugins.opentelemetry.OpenTelemetryClientConfig)
 */
@KtorDsl
public class OpenTelemetryClientConfig {
    /**
     * The [OpenTelemetryApi] instance providing tracer, meter, and propagators.
     * Individual properties ([tracerProvider], [meterProvider], [propagators]) override
     * the corresponding components from this instance when set.
     *
     * [Report a problem](https://ktor.io/feedback/?fqname=io.ktor.client.plugins.opentelemetry.OpenTelemetryClientConfig.openTelemetry)
     */
    public var openTelemetry: OpenTelemetryApi = OpenTelemetryApi.noop()

    /**
     * Overrides the [TracerProvider] from [openTelemetry].
     *
     * [Report a problem](https://ktor.io/feedback/?fqname=io.ktor.client.plugins.opentelemetry.OpenTelemetryClientConfig.tracerProvider)
     */
    public var tracerProvider: TracerProvider? = null

    /**
     * Overrides the [MeterProvider] from [openTelemetry].
     *
     * [Report a problem](https://ktor.io/feedback/?fqname=io.ktor.client.plugins.opentelemetry.OpenTelemetryClientConfig.meterProvider)
     */
    public var meterProvider: MeterProvider? = null

    /**
     * Overrides the [TextMapPropagator] from [openTelemetry].
     *
     * [Report a problem](https://ktor.io/feedback/?fqname=io.ktor.client.plugins.opentelemetry.OpenTelemetryClientConfig.propagators)
     */
    public var propagators: TextMapPropagator? = null

    /**
     * The instrumentation scope name used to obtain tracer and meter instances.
     *
     * [Report a problem](https://ktor.io/feedback/?fqname=io.ktor.client.plugins.opentelemetry.OpenTelemetryClientConfig.instrumentationName)
     */
    public var instrumentationName: String = "io.ktor.client"

    /**
     * The instrumentation scope version.
     *
     * [Report a problem](https://ktor.io/feedback/?fqname=io.ktor.client.plugins.opentelemetry.OpenTelemetryClientConfig.instrumentationVersion)
     */
    public var instrumentationVersion: String? = null

    internal val capturedRequestHeaders: MutableList<String> = mutableListOf()
    internal val capturedResponseHeaders: MutableList<String> = mutableListOf()

    /**
     * Captures the specified request header values as span attributes.
     * Attribute keys follow the pattern `http.request.header.<lowercase_header_name>`.
     *
     * @param headers header names to capture (case-insensitive)
     *
     * [Report a problem](https://ktor.io/feedback/?fqname=io.ktor.client.plugins.opentelemetry.OpenTelemetryClientConfig.captureRequestHeaders)
     */
    public fun captureRequestHeaders(vararg headers: String) {
        capturedRequestHeaders.addAll(headers.map { it.lowercase() })
    }

    /**
     * Captures the specified response header values as span attributes.
     * Attribute keys follow the pattern `http.response.header.<lowercase_header_name>`.
     *
     * @param headers header names to capture (case-insensitive)
     *
     * [Report a problem](https://ktor.io/feedback/?fqname=io.ktor.client.plugins.opentelemetry.OpenTelemetryClientConfig.captureResponseHeaders)
     */
    public fun captureResponseHeaders(vararg headers: String) {
        capturedResponseHeaders.addAll(headers.map { it.lowercase() })
    }

    internal var filter: (HttpRequestBuilder) -> Boolean = { true }

    /**
     * Filters which requests should be instrumented.
     * Return `true` to trace the request, `false` to skip.
     *
     * [Report a problem](https://ktor.io/feedback/?fqname=io.ktor.client.plugins.opentelemetry.OpenTelemetryClientConfig.filter)
     */
    public fun filter(predicate: (HttpRequestBuilder) -> Boolean) {
        filter = predicate
    }
}

private object RequestBuilderHeadersSetter : TextMapSetter<HttpRequestBuilder> {
    override fun set(carrier: HttpRequestBuilder?, key: String, value: String) {
        carrier?.headers?.remove(key)
        carrier?.headers?.append(key, value)
    }
}

/**
 * A plugin that enables OpenTelemetry distributed tracing and metrics for Ktor HTTP client requests.
 * Automatically creates client spans for outgoing requests, injects trace context into request headers,
 * and records request duration metrics following OpenTelemetry semantic conventions.
 *
 * When used together with the server-side OpenTelemetry plugin, trace context is automatically
 * propagated from server handlers to outgoing client requests via [Context.current].
 *
 * ```kotlin
 * val client = HttpClient(CIO) {
 *     install(OpenTelemetry) {
 *         openTelemetry = sdkOpenTelemetry
 *     }
 * }
 * ```
 *
 * [Report a problem](https://ktor.io/feedback/?fqname=io.ktor.client.plugins.opentelemetry.OpenTelemetry)
 *
 * @see OpenTelemetryClientConfig
 */
public val OpenTelemetry: ClientPlugin<OpenTelemetryClientConfig> =
    createClientPlugin("OpenTelemetry", ::OpenTelemetryClientConfig) {
        val otel = pluginConfig.openTelemetry
        val tracerProvider = pluginConfig.tracerProvider ?: otel.tracerProvider
        val meterProvider = pluginConfig.meterProvider ?: otel.meterProvider
        val propagator = pluginConfig.propagators ?: otel.propagators.textMapPropagator

        val tracer = tracerProvider.tracerBuilder(pluginConfig.instrumentationName).apply {
            pluginConfig.instrumentationVersion?.let { setInstrumentationVersion(it) }
        }.build()

        val meter = meterProvider.meterBuilder(pluginConfig.instrumentationName).apply {
            pluginConfig.instrumentationVersion?.let { setInstrumentationVersion(it) }
        }.build()

        val requestDuration = meter.histogramBuilder("http.client.request.duration")
            .setDescription("Duration of HTTP client requests")
            .setUnit("s")
            .build()

        val requestHeaders = pluginConfig.capturedRequestHeaders.toList()
        val responseHeaders = pluginConfig.capturedResponseHeaders.toList()
        val filter = pluginConfig.filter

        client.plugin(HttpSend).intercept { request ->
            if (!filter(request)) return@intercept execute(request)

            val parentContext = Context.current()
            val startTime = System.nanoTime()

            val span = tracer.spanBuilder("HTTP ${request.method.value}")
                .setParent(parentContext)
                .setSpanKind(SpanKind.CLIENT)
                .setAttribute(OtelAttributeKey.stringKey("http.request.method"), request.method.value)
                .setAttribute(OtelAttributeKey.stringKey("server.address"), request.url.host)
                .setAttribute(OtelAttributeKey.longKey("server.port"), request.url.port.toLong())
                .setAttribute(OtelAttributeKey.stringKey("url.full"), request.url.buildString())
                .startSpan()

            for (headerName in requestHeaders) {
                val values = request.headers.getAll(headerName)
                if (!values.isNullOrEmpty()) {
                    span.setAttribute(
                        OtelAttributeKey.stringArrayKey("http.request.header.$headerName"),
                        values
                    )
                }
            }

            propagator.inject(parentContext.with(span), request, RequestBuilderHeadersSetter)

            try {
                val call = execute(request)
                val statusCode = call.response.status.value

                span.setAttribute(OtelAttributeKey.longKey("http.response.status_code"), statusCode.toLong())

                for (headerName in responseHeaders) {
                    val values = call.response.headers.getAll(headerName)
                    if (!values.isNullOrEmpty()) {
                        span.setAttribute(
                            OtelAttributeKey.stringArrayKey("http.response.header.$headerName"),
                            values
                        )
                    }
                }

                if (statusCode >= 400) {
                    span.setStatus(StatusCode.ERROR)
                    span.setAttribute(OtelAttributeKey.stringKey("error.type"), statusCode.toString())
                }

                span.end()

                val durationSeconds = (System.nanoTime() - startTime) / 1_000_000_000.0
                requestDuration.record(
                    durationSeconds,
                    OtelAttributes.builder()
                        .put("http.request.method", request.method.value)
                        .put("http.response.status_code", statusCode.toLong())
                        .put("server.address", request.url.host)
                        .build()
                )

                call
            } catch (cause: Throwable) {
                val response = (cause as? ResponseException)?.response
                val statusCode = response?.status?.value
                
                span.recordException(cause)
                span.setStatus(StatusCode.ERROR, cause.message ?: "")
                span.setAttribute(
                    OtelAttributeKey.stringKey("error.type"),
                    cause::class.qualifiedName ?: "unknown"
                )
                if (statusCode != null) {
                    span.setAttribute(OtelAttributeKey.longKey("http.response.status_code"), statusCode.toLong())
                    span.setAttribute(OtelAttributeKey.stringKey("error.type"), statusCode.toString())
                    for (headerName in responseHeaders) {
                        val values = response.headers.getAll(headerName)
                        if (!values.isNullOrEmpty()) {
                            span.setAttribute(
                                OtelAttributeKey.stringArrayKey("http.response.header.$headerName"),
                                values
                            )
                        }
                    }
                } else {
                    span.setAttribute(
                        OtelAttributeKey.stringKey("error.type"),
                        cause::class.qualifiedName ?: "unknown"
                    )
                }
                span.end()

                val durationSeconds = (System.nanoTime() - startTime) / 1_000_000_000.0
                requestDuration.record(
                    durationSeconds,
                    OtelAttributes.builder()
                        .put("http.request.method", request.method.value)
                        .put("server.address", request.url.host)
                        .put("error.type", cause::class.qualifiedName ?: "unknown")
                        .build()
                )

                throw cause
            }
        }
    }
