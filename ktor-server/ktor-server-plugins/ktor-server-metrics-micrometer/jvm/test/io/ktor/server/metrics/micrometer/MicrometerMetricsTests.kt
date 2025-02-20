/*
 * Copyright 2014-2024 JetBrains s.r.o and contributors. Use of this source code is governed by the Apache 2.0 license.
 */

package io.ktor.server.metrics.micrometer

import io.ktor.client.request.*
import io.ktor.client.statement.*
import io.ktor.http.*
import io.ktor.server.application.*
import io.ktor.server.metrics.dropwizard.*
import io.ktor.server.response.*
import io.ktor.server.routing.*
import io.ktor.server.testing.*
import io.ktor.util.pipeline.*
import io.micrometer.core.instrument.*
import io.micrometer.core.instrument.binder.*
import io.micrometer.core.instrument.binder.jvm.*
import io.micrometer.core.instrument.binder.system.*
import io.micrometer.core.instrument.distribution.*
import io.micrometer.core.instrument.logging.*
import io.micrometer.core.instrument.simple.*
import java.io.*
import kotlin.reflect.*
import kotlin.test.*

class MicrometerMetricsTests {
    private var noHandlerHandledRequest = false
    private var throwableCaughtInEngine: Throwable? = null

    private val requestTimeTimerName = "ktor.http.server.requests"
    private val activeRequestsGaugeName = "ktor.http.server.requests.active"

    @BeforeTest
    fun reset() {
        noHandlerHandledRequest = false
        throwableCaughtInEngine = null
    }

    @Test
    fun `time is measured for requests`() = testApplication {
        val testRegistry = SimpleMeterRegistry()

        install(MicrometerMetrics) {
            registry = testRegistry
        }

        routing {
            get("/uri") {
                testRegistry.assertActive(1.0)
                call.respond("hello")
            }
        }

        client.request("/uri")

        val timers = testRegistry.find(requestTimeTimerName).timers()
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
    fun `time is not measured for custom http requests`() = testApplication {
        val testRegistry = SimpleMeterRegistry()
        install(MicrometerMetrics) {
            registry = testRegistry
        }

        routing {
            get("/uri") {
                call.respond("hello")
            }
        }

        var timers = testRegistry.find(requestTimeTimerName).timers()
        assertTrue(timers.isEmpty())

        client.get("/uri")
        timers = testRegistry.find(requestTimeTimerName).timers()
        assertEquals(1, timers.size, "Metrics should be recorded for GET requests")

        client.request("/uri") {
            method = HttpMethod("CUSTOM")
        }
        timers = testRegistry.find(requestTimeTimerName).timers()
        assertEquals(1, timers.size, "Metrics should not be recorded for custom requests")

        client.put("/uri")
        timers = testRegistry.find(requestTimeTimerName).timers()
        assertEquals(2, timers.size, "Metrics should be recorded for PUT requests")
    }

    @Test
    fun `errors are recorded`() = testApplication {
        val testRegistry = SimpleMeterRegistry()

        installDefaultBehaviour()

        install(MicrometerMetrics) {
            registry = testRegistry
        }

        routing {
            get("/uri") {
                testRegistry.assertActive(1.0)
                throw IllegalStateException("something went wrong")
            }
        }

        client.request("/uri")

        with(testRegistry.find(requestTimeTimerName).timers()) {
            assertEquals(1, size)
            this.first().run {
                assertTag("throwable", "java.lang.IllegalStateException")
                assertTag("status", "500")
                assertTag("route", "/uri")
                assertTag("method", "GET")
                assertTag("address", "localhost:80")
            }
        }
        testRegistry.assertActive(0.0)
        assertTrue(throwableCaughtInEngine is IllegalStateException)
    }

    @Test
    fun `parameter names are recorded instead of values`() = testApplication {
        val testRegistry = SimpleMeterRegistry()

        install(MicrometerMetrics) {
            registry = testRegistry
        }

        routing {
            get("/uri/{someParameter}") {
                call.respond("some response")
            }
        }

        client.request("/uri/someParameterValue")

        with(testRegistry.find(requestTimeTimerName).timers()) {
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
    fun `individual tags can be added per call`() = testApplication {
        val testRegistry = SimpleMeterRegistry()

        install(MicrometerMetrics) {
            registry = testRegistry
            timers { _, _ ->
                tag("customTag", "customValue")
            }
        }

        routing {
            get("/uri") {
                call.respond("some response")
            }
        }

        client.request("/uri")

        with(testRegistry.find(requestTimeTimerName).timers()) {
            assertEquals(1, size)
            this.first().run {
                assertTag("customTag", "customValue")
            }
        }
        testRegistry.assertActive(0.0)
    }

    private fun MeterRegistry.assertActive(expectedValue: Double) {
        assertEquals(expectedValue, this[activeRequestsGaugeName].gauge().value())
        assertEquals(expectedValue, this[activeRequestsGaugeName].gauge().value())
    }

    @Test
    fun `histogram can be configured`() = testApplication {
        val testRegistry = SimpleMeterRegistry()

        install(MicrometerMetrics) {
            registry = testRegistry
            distributionStatisticConfig = DistributionStatisticConfig.Builder()
                .percentiles(0.1, 0.2)
                .build()
        }

        routing {
            get("/uri") {
                call.respond("hello")
            }
        }
        client.request("/uri")

        val timers = testRegistry.find(requestTimeTimerName).timers()
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
    fun `no handler results in status 404 and no exception by default`() = testApplication {
        val testRegistry = SimpleMeterRegistry()

        install(MicrometerMetrics) {
            registry = testRegistry
        }

        installDefaultBehaviour()

        // no routing config

        client.request("/uri")

        with(testRegistry.find(requestTimeTimerName).timers()) {
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
        testApplication {
            val testRegistry = SimpleMeterRegistry()

            install(MicrometerMetrics) {
                registry = testRegistry
                distinctNotRegisteredRoutes = false
            }

            installDefaultBehaviour()

            // no routing config

            client.request("/uri")

            with(testRegistry.find(requestTimeTimerName).timers()) {
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

    private fun ApplicationTestBuilder.installDefaultBehaviour() {
        application {
            (engine as TestApplicationEngine).callInterceptor = {
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
    }

    @Test
    fun `class loader metrics are registered by default at registry`() = testApplication {
        metersAreRegistered(ClassLoaderMetrics::class, "jvm.classes.loaded", "jvm.classes.unloaded")
    }

    @Test
    fun `memory metrics are registered by default at registry`() = testApplication {
        metersAreRegistered(
            ClassLoaderMetrics::class,
            "jvm.memory.used",
            "jvm.memory.committed",
            "jvm.memory.max"
        )
    }

    @Test
    fun `garbage Collection metrics are registered by default at registry`() = testApplication {
        metersAreRegistered(
            JvmGcMetrics::class,
            "jvm.gc.max.data.size",
            "jvm.gc.live.data.size",
            "jvm.gc.memory.promoted",
            "jvm.gc.memory.allocated"
        )
    }

    @Test
    fun `processor metrics are registered by default at registry`() = testApplication {
        metersAreRegistered(
            ProcessorMetrics::class,
            "system.cpu.count"
        )
    }

    @Test
    fun `thread metrics are registered by default at registry`() = testApplication {
        metersAreRegistered(
            JvmThreadMetrics::class,
            "jvm.threads.peak",
            "jvm.threads.daemon",
            "jvm.threads.live",
            "jvm.threads.states"
        )
    }

    @Test
    fun `throws exception when metric name is not defined`() = testApplication {
        install(MicrometerMetrics) {
            metricName = "   "
        }

        assertFailsWith<IllegalArgumentException> {
            startApplication()
        }
    }

    @Test
    fun `timer and gauge metric names are configurable`() = testApplication {
        val newMetricName = "custom.metric.name"
        val registry = SimpleMeterRegistry()
        install(MicrometerMetrics) {
            this.registry = registry
            metricName = newMetricName
        }

        client.get("/uri")

        assertEquals(1, registry.get("$newMetricName.active").meters().size)
    }

    @Test
    fun `same timer and gauge metrics accessible by new and deprecated properties`() = testApplication {
        val testRegistry = SimpleMeterRegistry()

        install(MicrometerMetrics) {
            registry = testRegistry
        }

        routing {
            get("/uri") {
                testRegistry.assertActive(1.0)
                call.respondText { "hello" }
            }
        }

        client.request("/uri")

        with(testRegistry) {
            val timer = find(requestTimeTimerName).timer()
            val configurableTimer = find(requestTimeTimerName).timer()
            assertEquals(timer, configurableTimer)

            val gauge = find(activeRequestsGaugeName).gauge()
            val configurableGauge = find(activeRequestsGaugeName).gauge()
            assertEquals(gauge, configurableGauge)
        }
    }

    @Test
    fun `with DropwizardMetrics plugin`() = testApplication {
        application {
            install(MicrometerMetrics)
            install(DropwizardMetrics)

            routing {
                get("/") {
                    call.respondText { "OK" }
                }
            }
        }

        val response = client.get("/")
        assertEquals(HttpStatusCode.OK, response.status)
        assertEquals("OK", response.bodyAsText())
    }

    @Test
    fun `test closes previous registry`() = testApplication {
        var closed = false
        val metrics = object : LoggingMeterRegistry() {
            override fun close() {
                super.close()
                closed = true
            }
        }

        application {
            install(MicrometerMetrics) {
                registry = metrics
                registry = LoggingMeterRegistry()
            }
        }

        startApplication()
        assertTrue(closed)
    }

    @Test
    fun `register meter-filter before the very first meter is registered`() {
        val standardOut = System.out
        try {
            val outputStream = ByteArrayOutputStream()
            System.setOut(PrintStream(outputStream))

            val logs = outputStream.toString()
            assertFalse(logs.contains("MeterFilter is being configured after a Meter has been registered"))
        } finally {
            System.setOut(standardOut)
        }
    }

    private suspend fun ApplicationTestBuilder.metersAreRegistered(
        meterBinder: KClass<out MeterBinder>,
        vararg meterNames: String
    ) {
        val testRegistry = SimpleMeterRegistry()

        install(MicrometerMetrics) {
            registry = testRegistry
        }
        startApplication()

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
