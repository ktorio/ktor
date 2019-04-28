/*
 * Copyright 2014-2019 JetBrains s.r.o and contributors. Use of this source code is governed by the Apache 2.0 license.
 */

package io.ktor.metrics.micrometer

import io.ktor.application.*
import io.ktor.request.*
import io.ktor.response.*
import io.ktor.routing.*
import io.ktor.util.*
import io.ktor.util.pipeline.*
import io.micrometer.core.instrument.*


/**
 * Enables Micrometer support when installed. Exposes the following metrics:
 * <ul>
 *     <li><code>ktor.http.server.requests.active</code>: Gauge - The amount of active ktor requests</li>
 *     <li><code>ktor.http.server.requests</code>: Timer - Timer for all requests. By default no percentiles or
 *       histogram is exposed. Use the [Configuration.distributionStatisticConfig] to enable these.
 *       Tags by default (use [Configuration.tags] to configure the tags or add custom tags):
 *       <ul>
 *           <li><code>address</code>: The host and port of the request uri (e.g. 'www.ktor.io:443' from the uri
 *           'https://www.ktor.io/foo/bar' )</li>
 *           <li><code>method</code>: The http method (e.g. 'GET')</li>
 *           <li><code>route</code>: The use ktor route used for this request. (e.g. '/some/path/{someParameter}')
 *           <li><code>status</code>: The http status code that was set in the response) (or 404 if no handler was
 *           found for this request or 500 if an exception was thrown</li>
 *           <li><code>throwable</code>: The class name of the throwable that was eventually thrown while processing
 *           the request (or 'n/a' if no throwable had been thrown). Please note, that if an exception is thrown after
 *           calling [io.ktor.response.ApplicationResponseFunctionsKt.respond(io.ktor.application.ApplicationCall, java.lang.Object, kotlin.coroutines.Continuation<? super kotlin.Unit>)]
 *           , the tag is still "n/a"</li>
 *        <ul>
 *     <li>
 *  <ul>
 */
class MicrometerMetrics(config: Configuration) :
    AbstractMicrometerMetrics<MeterRegistry, MicrometerMetrics.Configuration>(config) {

    class Configuration : AbstractConfiguration<MeterRegistry>()

    /**
     * Micrometer feature installation object
     */
    companion object MetricsFeature : AbstractMetricsFeature<MeterRegistry, Configuration>(::MicrometerMetrics, ::Configuration) {

        /**
         * Request time timer name
         */
        @Deprecated(
            "Use AbstractMicrometerMetrics.activeGaugeName instead.",
            replaceWith = ReplaceWith("AbstractMicrometerMetrics.activeGaugeName"))
        const val activeGaugeName = AbstractMicrometerMetrics.activeGaugeName

        /**
         * Active requests gauge name
         */
        @Deprecated(
            "Use AbstractMicrometerMetrics.requestTimerName instead.",
            replaceWith = ReplaceWith("AbstractMicrometerMetrics.requestTimerName"))
        const val requestTimerName = AbstractMicrometerMetrics.requestTimerName
    }
}

/**
 * The meter registry registered for this feature
 */
val PipelineContext<Unit, ApplicationCall>.meterRegistry
    get() = call.application.feature(MicrometerMetrics).config.registry




