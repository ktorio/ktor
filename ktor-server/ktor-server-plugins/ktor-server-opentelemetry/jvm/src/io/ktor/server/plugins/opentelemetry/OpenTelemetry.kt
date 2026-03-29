/*
 * Copyright 2014-2025 JetBrains s.r.o and contributors. Use of this source code is governed by the Apache 2.0 license.
 */

package io.ktor.server.plugins.opentelemetry

import io.ktor.http.*
import io.ktor.http.HttpMethod.Companion.DefaultMethods
import io.ktor.server.application.*
import io.ktor.server.application.hooks.*
import io.ktor.server.request.*
import io.ktor.server.routing.*
import io.ktor.util.*
import io.ktor.util.pipeline.*
import io.ktor.utils.io.*
import io.opentelemetry.api.metrics.*
import io.opentelemetry.api.trace.*
import io.opentelemetry.context.Context
import io.opentelemetry.context.Scope
import io.opentelemetry.context.propagation.*
import kotlinx.coroutines.ThreadContextElement
import kotlinx.coroutines.withContext
import kotlin.coroutines.CoroutineContext
import io.opentelemetry.api.OpenTelemetry as OpenTelemetryApi
import io.opentelemetry.api.common.AttributeKey as OtelAttributeKey
import io.opentelemetry.api.common.Attributes as OtelAttributes

/**
 * Configuration for the [OpenTelemetry] server plugin.
 *
 * Supports two configuration styles:
 * - Set [openTelemetry] to provide an [OpenTelemetryApi] instance with all components configured.
 * - Set individual [tracerProvider], [meterProvider], and [propagators] properties to override
 *   specific components.
 *
 * [Report a problem](https://ktor.io/feedback/?fqname=io.ktor.server.plugins.opentelemetry.OpenTelemetryConfig)
 */
@KtorDsl
public class OpenTelemetryConfig {
    /**
     * The [OpenTelemetryApi] instance providing tracer, meter, and propagators.
     * Individual properties ([tracerProvider], [meterProvider], [propagators]) override
     * the corresponding components from this instance when set.
     *
     * [Report a problem](https://ktor.io/feedback/?fqname=io.ktor.server.plugins.opentelemetry.OpenTelemetryConfig.openTelemetry)
     */
    public var openTelemetry: OpenTelemetryApi = OpenTelemetryApi.noop()

    /**
     * Overrides the [TracerProvider] from [openTelemetry].
     *
     * [Report a problem](https://ktor.io/feedback/?fqname=io.ktor.server.plugins.opentelemetry.OpenTelemetryConfig.tracerProvider)
     */
    public var tracerProvider: TracerProvider? = null

    /**
     * Overrides the [MeterProvider] from [openTelemetry].
     *
     * [Report a problem](https://ktor.io/feedback/?fqname=io.ktor.server.plugins.opentelemetry.OpenTelemetryConfig.meterProvider)
     */
    public var meterProvider: MeterProvider? = null

    /**
     * Overrides the [TextMapPropagator] from [openTelemetry].
     *
     * [Report a problem](https://ktor.io/feedback/?fqname=io.ktor.server.plugins.opentelemetry.OpenTelemetryConfig.propagators)
     */
    public var propagators: TextMapPropagator? = null

    /**
     * The instrumentation scope name used to obtain tracer and meter instances.
     *
     * [Report a problem](https://ktor.io/feedback/?fqname=io.ktor.server.plugins.opentelemetry.OpenTelemetryConfig.instrumentationName)
     */
    public var instrumentationName: String = "io.ktor.server"

    /**
     * The instrumentation scope version.
     *
     * [Report a problem](https://ktor.io/feedback/?fqname=io.ktor.server.plugins.opentelemetry.OpenTelemetryConfig.instrumentationVersion)
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
     * [Report a problem](https://ktor.io/feedback/?fqname=io.ktor.server.plugins.opentelemetry.OpenTelemetryConfig.captureRequestHeaders)
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
     * [Report a problem](https://ktor.io/feedback/?fqname=io.ktor.server.plugins.opentelemetry.OpenTelemetryConfig.captureResponseHeaders)
     */
    public fun captureResponseHeaders(vararg headers: String) {
        capturedResponseHeaders.addAll(headers.map { it.lowercase() })
    }

    internal var spanNameExtractor: (ApplicationCall) -> String = { call ->
        "${call.request.httpMethod.value} ${call.request.path()}"
    }

    /**
     * Customizes the initial span name for each request.
     * The span name is automatically updated to use the route template when a route is resolved.
     *
     * [Report a problem](https://ktor.io/feedback/?fqname=io.ktor.server.plugins.opentelemetry.OpenTelemetryConfig.spanName)
     */
    public fun spanName(block: (ApplicationCall) -> String) {
        spanNameExtractor = block
    }

    internal var filter: (ApplicationCall) -> Boolean = { true }

    /**
     * Filters which requests should be instrumented.
     * Return `true` to trace the request, `false` to skip.
     *
     * [Report a problem](https://ktor.io/feedback/?fqname=io.ktor.server.plugins.opentelemetry.OpenTelemetryConfig.filter)
     */
    public fun filter(predicate: (ApplicationCall) -> Boolean) {
        filter = predicate
    }

    internal var transformRoute: (RoutingNode) -> String = { it.path }

    /**
     * Configures route label extraction from the resolved [RoutingNode].
     * Defaults to [RoutingNode.path].
     *
     * [Report a problem](https://ktor.io/feedback/?fqname=io.ktor.server.plugins.opentelemetry.OpenTelemetryConfig.transformRoute)
     */
    public fun transformRoute(block: (RoutingNode) -> String) {
        transformRoute = block
    }
}

private object HeadersTextMapGetter : TextMapGetter<Headers> {
    override fun keys(carrier: Headers): Iterable<String> = carrier.names()
    override fun get(carrier: Headers?, key: String): String? = carrier?.get(key)
}

/**
 * Coroutine context element that propagates the OpenTelemetry [Context] across coroutine dispatches.
 * Ensures that [Context.current] returns the correct OTEL context inside request handlers,
 * enabling automatic parent span detection in nested client calls.
 */
private class OtelCoroutineContextElement(
    private val otelContext: Context
) : ThreadContextElement<Scope> {
    companion object Key : CoroutineContext.Key<OtelCoroutineContextElement>

    override val key: CoroutineContext.Key<OtelCoroutineContextElement> get() = Key

    override fun updateThreadContext(context: CoroutineContext): Scope =
        otelContext.makeCurrent()

    override fun restoreThreadContext(context: CoroutineContext, oldState: Scope) {
        oldState.close()
    }
}

private val OtelContextHook = object : Hook<suspend (ApplicationCall, suspend () -> Unit) -> Unit> {
    override fun install(
        pipeline: ApplicationCallPipeline,
        handler: suspend (ApplicationCall, suspend () -> Unit) -> Unit
    ) {
        val phase = PipelinePhase("OpenTelemetryContext")
        pipeline.insertPhaseAfter(ApplicationCallPipeline.Monitoring, phase)
        pipeline.intercept(phase) {
            handler(call, ::proceed)
        }
    }
}

private data class CallTrace(
    val span: Span,
    val context: Context,
    val startTimeNanos: Long,
    var route: String? = null,
    var throwable: Throwable? = null
)

/**
 * A plugin that enables OpenTelemetry distributed tracing and metrics in your Ktor server application.
 * Automatically creates server spans for incoming HTTP requests, propagates trace context,
 * and records request duration metrics following OpenTelemetry semantic conventions.
 *
 * The plugin supports W3C Trace Context propagation, custom header capture, route-aware span naming,
 * and coroutine-safe context propagation for integration with the client-side [OpenTelemetry] plugin.
 *
 * ```kotlin
 * install(OpenTelemetry) {
 *     openTelemetry = sdkOpenTelemetry
 *     captureRequestHeaders("X-Request-ID")
 * }
 * ```
 *
 * [Report a problem](https://ktor.io/feedback/?fqname=io.ktor.server.plugins.opentelemetry.OpenTelemetry)
 *
 * @see OpenTelemetryConfig
 */
public val OpenTelemetry: ApplicationPlugin<OpenTelemetryConfig> =
    createApplicationPlugin("OpenTelemetry", ::OpenTelemetryConfig) {
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

        val requestDuration = meter.histogramBuilder("http.server.request.duration")
            .setDescription("Duration of HTTP server requests")
            .setUnit("s")
            .build()

        val activeRequests = meter.upDownCounterBuilder("http.server.active_requests")
            .setDescription("Number of active HTTP server requests")
            .build()

        val traceKey = AttributeKey<CallTrace>("openTelemetryTrace")
        val requestHeaders = pluginConfig.capturedRequestHeaders.toList()
        val responseHeaders = pluginConfig.capturedResponseHeaders.toList()
        val filter = pluginConfig.filter
        val spanNameExtractor = pluginConfig.spanNameExtractor
        val transformRoute = pluginConfig.transformRoute

        @OptIn(InternalAPI::class)
        on(Metrics) { call ->
            if (call.request.httpMethod !in DefaultMethods) return@on
            if (!filter(call)) return@on

            val parentContext = propagator.extract(
                Context.current(),
                call.request.headers,
                HeadersTextMapGetter
            )

            val spanBuilder = tracer.spanBuilder(spanNameExtractor(call))
                .setParent(parentContext)
                .setSpanKind(SpanKind.SERVER)
                .setAttribute(OtelAttributeKey.stringKey("http.request.method"), call.request.httpMethod.value)
                .setAttribute(OtelAttributeKey.stringKey("url.path"), call.request.path())
                .setAttribute(OtelAttributeKey.stringKey("url.scheme"), call.request.local.scheme)
                .setAttribute(OtelAttributeKey.stringKey("server.address"), call.request.local.serverHost)
                .setAttribute(OtelAttributeKey.longKey("server.port"), call.request.local.serverPort.toLong())
                .setAttribute(
                    OtelAttributeKey.stringKey("network.protocol.version"),
                    call.request.local.version.removePrefix("HTTP/")
                )

            call.request.userAgent()?.let {
                spanBuilder.setAttribute(OtelAttributeKey.stringKey("user_agent.original"), it)
            }

            for (headerName in requestHeaders) {
                val values = call.request.headers.getAll(headerName)
                if (!values.isNullOrEmpty()) {
                    spanBuilder.setAttribute(
                        OtelAttributeKey.stringArrayKey("http.request.header.$headerName"),
                        values
                    )
                }
            }

            val span = spanBuilder.startSpan()
            call.attributes.put(traceKey, CallTrace(span, parentContext.with(span), System.nanoTime()))
            activeRequests.add(1)
        }

        on(OtelContextHook) { call, proceed ->
            val trace = call.attributes.getOrNull(traceKey)
            if (trace != null) {
                withContext(OtelCoroutineContextElement(trace.context)) {
                    proceed()
                }
            } else {
                proceed()
            }
        }

        on(ResponseSent) { call ->
            val trace = call.attributes.getOrNull(traceKey) ?: return@on
            val statusCode = call.response.status()?.value ?: 0

            trace.span.setAttribute(OtelAttributeKey.longKey("http.response.status_code"), statusCode.toLong())

            trace.route?.let { route ->
                trace.span.setAttribute(OtelAttributeKey.stringKey("http.route"), route)
                trace.span.updateName("${call.request.httpMethod.value} $route")
            }

            for (headerName in responseHeaders) {
                val values = call.response.headers.values(headerName)
                if (values.isNotEmpty()) {
                    trace.span.setAttribute(
                        OtelAttributeKey.stringArrayKey("http.response.header.$headerName"),
                        values
                    )
                }
            }

            if (statusCode >= 500 || trace.throwable != null) {
                trace.span.setStatus(StatusCode.ERROR)
            }

            trace.throwable?.let { throwable ->
                trace.span.recordException(throwable)
                trace.span.setAttribute(
                    OtelAttributeKey.stringKey("error.type"),
                    throwable::class.qualifiedName ?: "unknown"
                )
            }

            trace.span.end()
            activeRequests.add(-1)

            val durationSeconds = (System.nanoTime() - trace.startTimeNanos) / 1_000_000_000.0
            requestDuration.record(
                durationSeconds,
                OtelAttributes.builder()
                    .put("http.request.method", call.request.httpMethod.value)
                    .put("http.response.status_code", statusCode.toLong())
                    .put("http.route", trace.route ?: call.request.path())
                    .put("url.scheme", call.request.local.scheme)
                    .build()
            )
        }

        on(CallFailed) { call, cause ->
            call.attributes.getOrNull(traceKey)?.throwable = cause
            throw cause
        }

        application.monitor.subscribe(RoutingRoot.RoutingCallStarted) { call ->
            call.attributes.getOrNull(traceKey)?.let { trace ->
                trace.route = transformRoute(call.route)
            }
        }
    }
