/*
 * Copyright 2014-2019 JetBrains s.r.o and contributors. Use of this source code is governed by the Apache 2.0 license.
 */

package io.ktor.metrics.dropwizard

import com.codahale.metrics.*
import io.ktor.application.*
import io.ktor.response.*
import io.ktor.routing.*
import io.ktor.server.testing.*
import org.junit.*
import org.junit.Assert.*


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

        assertEquals(1, testRegistry.meter("/uri/(method:GET).200").count)
    }
}
