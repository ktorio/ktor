/*
 * Copyright 2014-2026 JetBrains s.r.o and contributors. Use of this source code is governed by the Apache 2.0 license.
 */

package io.ktor.server.plugins.ratelimit.micrometer

import io.ktor.events.*
import io.ktor.server.application.*
import io.ktor.server.plugins.ratelimit.*
import io.ktor.server.routing.*
import io.ktor.utils.io.*
import io.micrometer.core.instrument.Counter
import io.micrometer.core.instrument.MeterRegistry
import io.micrometer.core.instrument.Tag
import io.micrometer.core.instrument.Tags
import io.micrometer.core.instrument.Timer
import io.micrometer.core.instrument.binder.MeterBinder
import kotlinx.coroutines.DisposableHandle
import kotlin.time.toJavaDuration

/**
 * A configuration for [RateLimitMetrics].
 *
 * [Report a problem](https://ktor.io/feedback/?fqname=io.ktor.server.plugins.ratelimit.micrometer.RateLimitMetricsConfig)
 */
@KtorDsl
public class RateLimitMetricsConfig {
    /**
     * Specifies the base name (prefix) of the registered meters.
     * For example, the default "ktor.rate.limit" value results in the following meters:
     * - "ktor.rate.limit.requests"
     * - "ktor.rate.limit.retry.after"
     *
     * [Report a problem](https://ktor.io/feedback/?fqname=io.ktor.server.plugins.ratelimit.micrometer.RateLimitMetricsConfig.metricName)
     */
    public var metricName: String = "ktor.rate.limit"

    internal val tags = mutableListOf<Pair<String, (RateLimitEventData) -> String>>()

    /**
     * Adds a tag with the [key] to the meters recorded for each [RateLimitEventData].
     * The tag value is computed by [value] for each event.
     * Avoid high-cardinality values, such as the request key, because each distinct tag value creates a new time series.
     *
     * ```kotlin
     * addTag("tier") { event -> if (event.limiter.providerName == "premium") "paid" else "free" }
     * ```
     *
     * [Report a problem](https://ktor.io/feedback/?fqname=io.ktor.server.plugins.ratelimit.micrometer.RateLimitMetricsConfig.addTag)
     *
     * @param key the tag key.
     * @param value a function that computes the tag value for the event.
     */
    public fun addTag(key: String, value: (RateLimitEventData) -> String) {
        require(key.isNotBlank()) { "Tag key should not be blank" }
        tags.add(key to value)
    }
}

/**
 * A [MeterBinder] that records metrics for the RateLimit plugin by subscribing to [RateLimitEvent].
 *
 * The following meters are registered:
 * - `<metricName>.requests`: a counter of requests checked by a rate limiter,
 *   tagged with `limiter`, `route`, `method` and `result` (`allowed` or `rejected`).
 * - `<metricName>.retry.after`: a timer of the wait duration returned to rejected requests,
 *   tagged with `limiter`, `route` and `method`.
 *
 * The `route` tag is `n/a` for the global rate limiter because it runs before routing.
 *
 * Add the binder to the `MicrometerMetrics` plugin configuration:
 * ```kotlin
 * install(MicrometerMetrics) {
 *     registry = prometheusMeterRegistry
 *     meterBinders += RateLimitMetrics(monitor) {
 *         addTag("tier") { event -> if (event.limiter.providerName == "premium") "paid" else "free" }
 *     }
 * }
 * ```
 *
 * [Report a problem](https://ktor.io/feedback/?fqname=io.ktor.server.plugins.ratelimit.micrometer.RateLimitMetrics)
 *
 * @param monitor the application events monitor to subscribe to, usually [Application.monitor].
 * @param configure the [RateLimitMetricsConfig] configuration block.
 */
public class RateLimitMetrics(
    private val monitor: Events,
    configure: RateLimitMetricsConfig.() -> Unit = {},
) : MeterBinder {
    private val config = RateLimitMetricsConfig().apply(configure)

    init {
        require(config.metricName.isNotBlank()) { "Metric name should not be blank" }
    }

    override fun bindTo(registry: MeterRegistry) {
        val requestsName = "${config.metricName}.requests"
        val retryAfterName = "${config.metricName}.retry.after"
        val additionalTags = config.tags.toList()

        val handle = monitor.subscribe(RateLimitEvent) { event ->
            val commonTags = Tags.of(
                Tag.of("limiter", event.limiter.providerName),
                Tag.of("route", event.route?.path() ?: "n/a"),
                Tag.of("method", event.method.value),
            ).and(additionalTags.map { (key, value) -> Tag.of(key, value(event)) })

            fun requests(result: String) = Counter.builder(requestsName)
                .description("Number of requests checked by a rate limiter")
                .tags(commonTags.and("result", result))
                .register(registry)

            when (event) {
                is RateLimitEventData.Allowed -> requests("allowed").increment()

                is RateLimitEventData.Rejected -> {
                    requests("rejected").increment()
                    Timer.builder(retryAfterName)
                        .description("Time that rejected clients are told to wait before retrying")
                        .tags(commonTags)
                        .register(registry)
                        .record(event.toWait.toJavaDuration())
                }
            }
        }

        var stoppedHandle: DisposableHandle? = null
        stoppedHandle = monitor.subscribe(ApplicationStopped) {
            handle.dispose()
            stoppedHandle?.dispose()
        }
    }
}
