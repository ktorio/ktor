/*
 * Copyright 2014-2019 JetBrains s.r.o and contributors. Use of this source code is governed by the Apache 2.0 license.
 */

package io.ktor.metrics.micrometer.prometheus

import io.ktor.application.Application
import io.ktor.application.ApplicationCall
import io.ktor.application.call
import io.ktor.application.feature
import io.ktor.http.ContentType
import io.ktor.http.charset
import io.ktor.http.content.OutgoingContent
import io.ktor.metrics.micrometer.AbstractMicrometerMetrics
import io.ktor.response.ApplicationSendPipeline
import io.ktor.response.respond
import io.ktor.routing.get
import io.ktor.routing.routing
import io.ktor.util.cio.bufferedWriter
import io.micrometer.core.instrument.distribution.DistributionStatisticConfig
import io.micrometer.prometheus.PrometheusConfig
import io.micrometer.prometheus.PrometheusMeterRegistry
import io.prometheus.client.Collector
import io.prometheus.client.exporter.common.TextFormat
import kotlinx.coroutines.io.ByteWriteChannel
import java.util.*

class MicrometerMetricsPrometheus(config: Configuration) :
    AbstractMicrometerMetrics<PrometheusMeterRegistry, MicrometerMetricsPrometheus.Configuration>(config) {

    internal fun process(value: MetricsContent) = object : OutgoingContent.WriteChannelContent() {

        override val contentType = ContentType.parse(TextFormat.CONTENT_TYPE_004)

        override suspend fun writeTo(channel: ByteWriteChannel) {
            val metricSamplesToScrape = value.filteredMetricFamilySamples()

            channel.bufferedWriter(contentType.charset() ?: Charsets.UTF_8).use {
                TextFormat.write004(it, metricSamplesToScrape)
            }
        }
    }

    class Configuration : AbstractMicrometerMetrics.AbstractConfiguration<PrometheusMeterRegistry>() {

        /**
         * The meter registry
         */
        override var registry = PrometheusMeterRegistry(PrometheusConfig.DEFAULT)

        override var distributionStatisticConfig: DistributionStatisticConfig = DistributionStatisticConfig.Builder()
            .percentilesHistogram(true)
            .build()

        /**
         * Defines if a metrics endpoint is installed under "/metrics" for all metrics registered at [registry].
         * Set to false if you want to declare a custom metric endpoint instead.
         */
        var installDefaultEndpoint = true
    }


    companion object MetricsFeature :
        AbstractMetricsFeature<PrometheusMeterRegistry, Configuration>(
            ::MicrometerMetricsPrometheus,
            MicrometerMetricsPrometheus::Configuration
        ) {

        override fun install(
            pipeline: Application,
            configure: Configuration.() -> Unit
        ): AbstractMicrometerMetrics<PrometheusMeterRegistry, Configuration> {
            val feature = super.install(pipeline, configure) as MicrometerMetricsPrometheus

            pipeline.sendPipeline.intercept(ApplicationSendPipeline.Transform) { value ->
                if (value is MetricsContent) {
                    val response = feature.process(value)
                    proceedWith(response)
                }
            }

            if (feature.config.installDefaultEndpoint)
                pipeline.routing {
                    get("/metrics") {
                        call.respondMetrics()
                    }
                }

            return feature
        }
    }
}


/**
 * the meter registry registered for this feature
 */
val ApplicationCall.meterRegistry
    get() = application.feature(MicrometerMetricsPrometheus).config.registry

/**
 * responds with scraped metrics of this registry, filtered by the specified filter. The filter works after the names
 * of the metrics have been "prometheusized" meaning that the names and tags
 */
suspend fun ApplicationCall.respondMetrics(
    registry: PrometheusMeterRegistry = meterRegistry,
    filter: Collector.MetricFamilySamples.() -> Boolean = { true }
) =
    respond(MetricsContent(registry, filter))


internal class MetricsContent(
    val registry: PrometheusMeterRegistry,
    val filter: Collector.MetricFamilySamples.() -> Boolean
) {
    fun filteredMetricFamilySamples(): Enumeration<Collector.MetricFamilySamples> =
        object : Enumeration<Collector.MetricFamilySamples> {

            private val internalCollection =
                registry.prometheusRegistry.metricFamilySamples()

            private var currentElement: Collector.MetricFamilySamples? = findNext()

            private fun findNext(): Collector.MetricFamilySamples? {
                var next: Collector.MetricFamilySamples?
                do {
                    if (internalCollection.hasMoreElements())
                        next = internalCollection.nextElement()
                    else
                        next = null
                } while (next != null && !acceptedByFilter(next))

                return next
            }

            private fun acceptedByFilter(samples: Collector.MetricFamilySamples) =
                filter(samples)

            override fun hasMoreElements(): Boolean =
                currentElement != null

            override fun nextElement(): Collector.MetricFamilySamples {
                val result = currentElement
                currentElement = findNext()
                return result ?: throw NoSuchElementException()
            }
        }
}

