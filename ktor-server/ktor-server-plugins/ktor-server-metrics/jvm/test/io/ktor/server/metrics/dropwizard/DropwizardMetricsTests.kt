/*
 * Copyright 2014-2021 JetBrains s.r.o and contributors. Use of this source code is governed by the Apache 2.0 license.
 */

package io.ktor.server.metrics.dropwizard

import com.codahale.metrics.*
import com.codahale.metrics.jvm.*
import io.ktor.server.application.*
import io.ktor.server.response.*
import io.ktor.server.routing.*
import io.ktor.server.testing.*
import org.hamcrest.CoreMatchers.*
import org.hamcrest.MatcherAssert.*
import org.junit.*
import org.junit.Test
import kotlin.test.*

@Suppress("DEPRECATION")
class DropwizardMetricsTests {

    @Test
    fun `meter is registered for a given path`(): Unit = withTestApplication {
        val testRegistry = MetricRegistry()

        application.install(DropwizardMetrics) {
            registry = testRegistry
        }

        application.routing {
            get("/uri") {
                call.respond("hello")
            }
        }

        handleRequest {
            uri = "/uri"
        }

        assertEquals(1, testRegistry.meter("ktor.calls./uri/(method:GET).200").count)
    }

    @Test
    fun `should not throw exception if metric already registered`(): Unit = withTestApplication {
        val testRegistry = MetricRegistry()
        testRegistry.register("jvm.memory", MemoryUsageGaugeSet())

        application.install(DropwizardMetrics) {
            registry = testRegistry
            baseName = ""
        }

        application.routing {
            get("/uri") {
                call.respond("hello")
            }
        }

        handleRequest {
            uri = "/uri"
        }

        assertEquals(1, testRegistry.meter("/uri/(method:GET).200").count)
    }

    @Test
    fun `jvm metrics are not registered when disabled in config`(): Unit = withTestApplication {
        val testRegistry = MetricRegistry()

        application.install(DropwizardMetrics) {
            registry = testRegistry
            registerJvmMetricSets = false
        }

        assertEquals(setOf("ktor.calls.active", "ktor.calls.duration", "ktor.calls.exceptions"), testRegistry.names)
    }

    @Test
    fun `should prefix all metrics with baseName`(): Unit = withTestApplication {
        val prefix = "foo.bar"
        val registry = MetricRegistry()
        application.install(DropwizardMetrics) {
            baseName = prefix
            registerJvmMetricSets = false
            this.registry = registry
        }

        application.routing {
            get("/uri") {
                call.respond("hello")
            }
        }

        handleRequest { uri = "/uri" }

        assertThat(registry.names, everyItem(startsWith(prefix)))
    }
}
