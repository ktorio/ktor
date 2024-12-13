/*
 * Copyright 2014-2024 JetBrains s.r.o and contributors. Use of this source code is governed by the Apache 2.0 license.
 */

package io.ktor.server.metrics.dropwizard

import com.codahale.metrics.MetricRegistry
import com.codahale.metrics.jvm.MemoryUsageGaugeSet
import io.ktor.client.request.*
import io.ktor.http.*
import io.ktor.server.plugins.cors.routing.*
import io.ktor.server.plugins.statuspages.*
import io.ktor.server.response.*
import io.ktor.server.routing.*
import io.ktor.server.testing.*
import io.ktor.test.junit.*
import org.junit.jupiter.api.Assertions.assertEquals
import kotlin.test.Test

class DropwizardMetricsTests {

    @Test
    fun `meter is registered for a given path`() = testApplication {
        val testRegistry = MetricRegistry()

        install(DropwizardMetrics) {
            registry = testRegistry
        }

        routing {
            get("/uri") {
                call.respond("hello")
            }
        }

        client.request("/uri")

        assertEquals(1, testRegistry.meter("ktor.calls./uri/(method:GET).200").count)
    }

    @Test
    fun `should not throw exception if metric already registered`() = testApplication {
        val testRegistry = MetricRegistry()
        testRegistry.register("jvm.memory", MemoryUsageGaugeSet())

        install(DropwizardMetrics) {
            registry = testRegistry
            baseName = ""
        }

        routing {
            get("/uri") {
                call.respond("hello")
            }
        }

        client.request("/uri")

        assertEquals(1, testRegistry.meter("/uri/(method:GET).200").count)
    }

    @Test
    fun `jvm metrics are not registered when disabled in config`() = testApplication {
        val testRegistry = MetricRegistry()

        install(DropwizardMetrics) {
            registry = testRegistry
            registerJvmMetricSets = false
        }
        startApplication()

        assertEquals(setOf("ktor.calls.active", "ktor.calls.duration", "ktor.calls.exceptions"), testRegistry.names)
    }

    @Test
    fun `should prefix all metrics with baseName`() = testApplication {
        val prefix = "foo.bar"
        val registry = MetricRegistry()
        install(DropwizardMetrics) {
            baseName = prefix
            registerJvmMetricSets = false
            this.registry = registry
        }

        routing {
            get("/uri") {
                call.respond("hello")
            }
        }

        client.request("/uri")

        assertAll(registry.names, "All registry names should start with prefix $prefix") { name ->
            name.startsWith(prefix)
        }
    }

    @Test
    fun `with StatusPages plugin`() = testApplication {
        install(StatusPages) {
            exception<Throwable> { call, _ ->
                call.respond(HttpStatusCode.InternalServerError)
            }
        }

        val testRegistry = MetricRegistry()
        install(DropwizardMetrics) {
            registry = testRegistry
            baseName = ""
        }

        routing {
            get("/uri") {
                throw RuntimeException("Oops")
            }
        }

        client.get("/uri")

        assertEquals(1, testRegistry.meter("/uri/(method:GET).500").count)
    }

    @Test
    fun `with CORS plugin`() = testApplication {
        val testRegistry = MetricRegistry()
        install(DropwizardMetrics) {
            registry = testRegistry
        }
        install(CORS) {
            anyHost()
        }

        routing {
            get("/") {
                call.respond("Hello, World")
            }
        }

        val response = client.options("") {
            header(HttpHeaders.AccessControlRequestMethod, "GET")
            header(HttpHeaders.Origin, "https://ktor.io")
        }

        assertEquals(HttpStatusCode.OK, response.status)
        assertEquals(1, testRegistry.meter("ktor.calls./(method:OPTIONS).200").count)
    }
}
