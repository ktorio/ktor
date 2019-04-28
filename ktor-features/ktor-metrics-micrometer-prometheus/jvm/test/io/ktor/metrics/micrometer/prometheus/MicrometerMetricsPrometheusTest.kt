/*
 * Copyright 2014-2019 JetBrains s.r.o and contributors. Use of this source code is governed by the Apache 2.0 license.
 */

package io.ktor.metrics.micrometer.prometheus

import io.ktor.application.call
import io.ktor.application.install
import io.ktor.response.respond
import io.ktor.routing.get
import io.ktor.routing.routing
import io.ktor.server.testing.TestApplicationCall
import io.ktor.server.testing.TestApplicationEngine
import io.ktor.server.testing.withTestApplication
import io.micrometer.prometheus.PrometheusConfig
import io.micrometer.prometheus.PrometheusMeterRegistry
import org.junit.Test
import kotlin.test.assertTrue

class MicrometerMetricsPrometheusTest {

    @Test
    fun `metrics can be scraped`() {

        withTestApplication {
            application.install(MicrometerMetricsPrometheus)

            val call = handleMetricsRequest()

            assertTrue { call.requestHandled }
            assertMetricsInResult(call)
        }
    }

    private fun assertMetricsInResult(call: TestApplicationCall) {
        assertTrue("call does not contain gauge") {
            call.response.content?.contains("ktor_http_server_requests_active")
                ?: throw Exception("no result")
        }
    }

    @Test
    fun `metrics contains histograms`() {

        withTestApplication {
            application.install(MicrometerMetricsPrometheus) {
                this@install.meterBinders = listOf() // keep the test simple
            }
            // first we have to make a business request, otherwise the histogram will not exist
            handleBusinessRequest()

            val call = handleMetricsRequest()

            assertTrue { call.requestHandled }

            assertTrue("call does not contain gauge") {
                call.response.content?.contains("ktor_http_server_requests_seconds_bucket")
                    ?: throw Exception("no result")
            }
        }
    }

    @Test
    fun `metrics can be filtered`() {
        withTestApplication {

            val registry = PrometheusMeterRegistry(PrometheusConfig.DEFAULT)
            registry.gauge("hello", 4711)

            application.install(MicrometerMetricsPrometheus) {
                this.registry = registry
            }

            application.routing {
                get("filteredMetrics") {
                    call.respondMetrics { name.contains("hello") }
                }
            }

            val result = handleRequest { uri = "/filteredMetrics" }

            with(result.response.content!!) {
                assertTrue { contains("hello 4711.0") } // the only metric that is accepted
                assertTrue { !contains("active") } // no active metric
            }

        }
    }

    private fun TestApplicationEngine.handleMetricsRequest() =
        handleRequest {
            uri = "/metrics"
        }

    private fun TestApplicationEngine.handleBusinessRequest() {

        application.routing {
            get("/") {
                call.respond("hello world")
            }
        }

        handleRequest {
            uri = "/"
        }

    }
}
