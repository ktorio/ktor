/*
 * Copyright 2014-2019 JetBrains s.r.o and contributors. Use of this source code is governed by the Apache 2.0 license.
 */

package io.ktor.metrics.micrometer

import io.ktor.application.*
import io.ktor.http.*
import io.ktor.response.*
import io.ktor.routing.*
import io.ktor.server.testing.*
import io.ktor.util.pipeline.*
import io.micrometer.core.instrument.*
import io.micrometer.core.instrument.binder.*
import io.micrometer.core.instrument.binder.jvm.*
import io.micrometer.core.instrument.binder.system.*
import io.micrometer.core.instrument.distribution.*
import io.micrometer.core.instrument.simple.*
import kotlin.reflect.*
import kotlin.test.*

class MicrometerMetricsTests {

    var noHandlerHandledRequest = false
    var throwableCaughtInEngine: Throwable? = null

    @BeforeTest
    fun reset() {
        noHandlerHandledRequest = false
        throwableCaughtInEngine = null
    }

    @Test
    fun `time is measured for requests`(): Unit = withTestApplication {
        val testRegistry = SimpleMeterRegistry()

        application.install(MicrometerMetrics) {
            registry = testRegistry
        }

        application.routing {
            get("/uri") {
                testRegistry.assertActive(1.0)
                call.respond("hello")
            }
        }

        handleRequest {
            uri = "/uri"
        }

        val timers = testRegistry.find(MicrometerMetrics.requestTimerName).timers()
        assertEquals(1, timers.size)
        timers.first().run {
            assertTag("throwable", "n/a")
            assertTag("status", "200")
            assertTag("route", "/uri")
            assertTag("method", "GET")
            assertTag("address", "localhost:80")
        }

        testRegistry.assertActive(0.0)
    }

    @Test
    fun `errors are recorded`(): Unit = withTestApplication {
        val testRegistry = SimpleMeterRegistry()

        installDefaultBehaviour()

        application.install(MicrometerMetrics) {
            registry = testRegistry
        }

        val illegalAccessException = IllegalAccessException("something went wrong")

        application.routing {
            get("/uri") {
                testRegistry.assertActive(1.0)
                throw illegalAccessException
            }
        }

        handleRequest {
            uri = "/uri"
        }

        with(testRegistry.find(MicrometerMetrics.requestTimerName).timers()) {
            assertEquals(1, size)
            this.first().run {
                assertTag("throwable", "java.lang.IllegalAccessException")
                assertTag("status", "500")
                assertTag("route", "/uri")
                assertTag("method", "GET")
                assertTag("address", "localhost:80")
            }
        }
        testRegistry.assertActive(0.0)
        assertTrue(throwableCaughtInEngine is IllegalAccessException)
    }

    @Test
    fun `parameter names are recorded instead of values`(): Unit = withTestApplication {
        val testRegistry = SimpleMeterRegistry()

        application.install(MicrometerMetrics) {
            registry = testRegistry
        }

        application.routing {
            get("/uri/{someParameter}") {
                call.respond("some response")
            }
        }

        handleRequest {
            uri = "/uri/someParameterValue"
        }

        with(testRegistry.find(MicrometerMetrics.requestTimerName).timers()) {
            assertEquals(1, size)
            this.first().run {
                assertTag("throwable", "n/a")
                assertTag("status", "200")
                assertTag("route", "/uri/{someParameter}")
                assertTag("method", "GET")
                assertTag("address", "localhost:80")
            }
        }
    }

    @Test
    fun `individual tags can be added per call`(): Unit = withTestApplication {
        val testRegistry = SimpleMeterRegistry()

        application.install(MicrometerMetrics) {
            registry = testRegistry
            timers { _, _ ->
                tag("customTag", "customValue")
            }
        }

        application.routing {
            get("/uri") {
                call.respond("some response")
            }
        }

        handleRequest {
            uri = "/uri"
        }

        with(testRegistry.find(MicrometerMetrics.requestTimerName).timers()) {
            assertEquals(1, size)
            this.first().run {
                assertTag("customTag", "customValue")
            }
        }
        testRegistry.assertActive(0.0)
    }

    private fun MeterRegistry.assertActive(expectedValue: Double) {
        assertEquals(expectedValue, this[MicrometerMetrics.activeGaugeName].gauge().value())
        assertEquals(expectedValue, this[MicrometerMetrics.activeRequestsGaugeName].gauge().value())
    }

    @Test
    fun `histogram can be configured`(): Unit = withTestApplication {
        val testRegistry = SimpleMeterRegistry()

        application.install(MicrometerMetrics) {
            registry = testRegistry
            distributionStatisticConfig = DistributionStatisticConfig.Builder()
                .percentiles(0.1, 0.2)
                .build()
        }

        application.routing {
            get("/uri") {
                call.respond("hello")
            }
        }
        handleRequest {
            uri = "/uri"
        }

        val timers = testRegistry.find(MicrometerMetrics.requestTimerName).timers()
        assertEquals(1, timers.size)
        val percentileValues = timers.first().takeSnapshot().percentileValues()
        assertEquals(
            1,
            percentileValues.count { it.percentile() == 0.1 },
            "$percentileValues should contain a 0.1 percentile"
        )
        assertEquals(
            1,
            percentileValues.count { it.percentile() == 0.2 },
            "$percentileValues should contain a 0.2 percentile"
        )
    }

    @Test
    fun `no handler results in status 404 and no exception by default`(): Unit = withTestApplication {
        val testRegistry = SimpleMeterRegistry()

        application.install(MicrometerMetrics) {
            registry = testRegistry
        }

        installDefaultBehaviour()

        // no routing config

        handleRequest {
            uri = "/uri"
        }

        with(testRegistry.find(MicrometerMetrics.requestTimerName).timers()) {
            assertEquals(1, size)
            this.first().run {
                assertTag("throwable", "n/a")
                assertTag("status", "404")
                assertTag("route", "/uri")
                assertTag("method", "GET")
                assertTag("address", "localhost:80")
            }
        }

        assertNull(throwableCaughtInEngine)
        assertTrue(noHandlerHandledRequest)
    }

    @Test
    fun `no handler results in status 404 no route and no exception if distinctNotRegisteredRoutes is false`(): Unit =
        withTestApplication {
            val testRegistry = SimpleMeterRegistry()

            application.install(MicrometerMetrics) {
                registry = testRegistry
                distinctNotRegisteredRoutes = false
            }

            installDefaultBehaviour()

            // no routing config

            handleRequest {
                uri = "/uri"
            }

            with(testRegistry.find(MicrometerMetrics.requestTimerName).timers()) {
                assertEquals(1, size)
                this.first().run {
                    assertTag("throwable", "n/a")
                    assertTag("status", "404")
                    assertTag("route", "n/a")
                    assertTag("method", "GET")
                    assertTag("address", "localhost:80")
                }
            }

            assertNull(throwableCaughtInEngine)
            assertTrue(noHandlerHandledRequest)
        }

    private fun TestApplicationEngine.installDefaultBehaviour() {
        this.callInterceptor = {
            try {
                call.application.execute(call)
                if (call.response.status() == HttpStatusCode.NotFound) {
                    noHandlerHandledRequest = true
                }
            } catch (t: Throwable) {
                throwableCaughtInEngine = t
                if (call.response.status() == null) {
                    call.respond(HttpStatusCode.InternalServerError)
                }
            }
        }
    }

    @Test
    fun `class loader metrics are registered by default at registry`(): Unit = withTestApplication {
        metersAreRegistered(ClassLoaderMetrics::class, "jvm.classes.loaded", "jvm.classes.unloaded")
    }

    @Test
    fun `memory metrics are registered by default at registry`(): Unit = withTestApplication {
        metersAreRegistered(
            ClassLoaderMetrics::class,
            "jvm.memory.used",
            "jvm.memory.committed",
            "jvm.memory.max"
        )
    }

    @Test
    fun `garbage Collection metrics are registered by default at registry`(): Unit = withTestApplication {
        metersAreRegistered(
            JvmGcMetrics::class,
            "jvm.gc.max.data.size",
            "jvm.gc.live.data.size",
            "jvm.gc.memory.promoted",
            "jvm.gc.memory.allocated"
        )
    }

    @Test
    fun `processor metrics are registered by default at registry`(): Unit = withTestApplication {
        metersAreRegistered(
            ProcessorMetrics::class,
            "system.cpu.count"
        )
    }

    @Test
    fun `thread metrics are registered by default at registry`(): Unit = withTestApplication {
        metersAreRegistered(
            JvmThreadMetrics::class,
            "jvm.threads.peak",
            "jvm.threads.daemon",
            "jvm.threads.live",
            "jvm.threads.states"
        )
    }

    @Test
    fun `throws exception when base name is not defined`(): Unit = withTestApplication {
        assertFailsWith<IllegalArgumentException> {
            application.install(MicrometerMetrics) {
                baseName = "   "
            }
        }
    }

    @Test
    fun `timer and gauge metric names are configurable`(): Unit = withTestApplication {
        val newBaseName = "custom.http.server"
        application.install(MicrometerMetrics) {
            registry = SimpleMeterRegistry()
            baseName = newBaseName
        }

        assertEquals("$newBaseName.requests", MicrometerMetrics.requestTimeTimerName)
        assertEquals("$newBaseName.requests.active", MicrometerMetrics.activeRequestsGaugeName)
    }

    @Test
    fun `same timer and gauge metrics accessible by new and deprecated properties`(): Unit = withTestApplication {
        val testRegistry = SimpleMeterRegistry()

        application.install(MicrometerMetrics) {
            registry = testRegistry
        }

        application.routing {
            get("/uri") {
                testRegistry.assertActive(1.0)
                call.respond("hello")
            }
        }

        handleRequest {
            uri = "/uri"
        }

        with(testRegistry) {
            val timer = find(MicrometerMetrics.requestTimerName).timer()
            val configurableTimer = find(MicrometerMetrics.requestTimeTimerName).timer()
            assertEquals(timer, configurableTimer)

            val gauge = find(MicrometerMetrics.activeGaugeName).gauge()
            val configurableGauge = find(MicrometerMetrics.activeRequestsGaugeName).gauge()
            assertEquals(gauge, configurableGauge)
        }
    }

    private fun TestApplicationEngine.metersAreRegistered(
        meterBinder: KClass<out MeterBinder>,
        vararg meterNames: String
    ) {
        val testRegistry = SimpleMeterRegistry()

        application.install(MicrometerMetrics) {
            registry = testRegistry
        }

        meterNames.forEach { testRegistry.shouldHaveMetricFrom(meterBinder, it) }
    }

    private fun MeterRegistry.shouldHaveMetricFrom(
        meterRegistry: KClass<out MeterBinder>,
        meterName: String
    ) {
        assertNotEquals(
            listOf(),
            this.find(meterName).meters(),
            "should have a metrics from ${meterRegistry.qualifiedName}"
        )
    }

    private fun Meter.assertTag(tagName: String, expectedValue: String) {
        val tag = this.id.tags.find { it.key == tagName }

        assertNotNull(tag, "$this does not contain a tag named '$tagName'")
        assertEquals(expectedValue, tag.value, "Tag value for '$tagName' should be '$expectedValue'")
    }
}
