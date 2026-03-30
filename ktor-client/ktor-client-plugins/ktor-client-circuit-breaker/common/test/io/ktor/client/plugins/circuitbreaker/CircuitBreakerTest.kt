/*
 * Copyright 2014-2025 JetBrains s.r.o and contributors. Use of this source code is governed by the Apache 2.0 license.
 */

package io.ktor.client.plugins.circuitbreaker

import io.ktor.client.*
import io.ktor.client.engine.mock.*
import io.ktor.client.request.*
import io.ktor.http.*
import io.ktor.test.dispatcher.*
import kotlin.test.*
import kotlin.time.*
import kotlin.time.Duration.Companion.seconds

class CircuitBreakerTest {

    @Test
    fun `requests pass through a closed circuit`() = testSuspend {
        val client = createTestClient(respondWith = { HttpStatusCode.OK }) {
            register("svc") {
                failureThreshold = 5
                resetTimeout = 30.seconds
            }
        }

        repeat(10) {
            client.get("http://svc/") { circuitBreaker("svc") }
        }

        client.close()
    }

    @Test
    fun `circuit opens after reaching failure threshold`() = testSuspend {
        val client = createTestClient(respondWith = { HttpStatusCode.InternalServerError }) {
            register("svc") {
                failureThreshold = 3
                resetTimeout = 30.seconds
            }
        }

        repeat(3) {
            client.get("http://svc/") { circuitBreaker("svc") }
        }

        assertFailsWith<CircuitBreakerOpenException> {
            client.get("http://svc/") { circuitBreaker("svc") }
        }

        client.close()
    }

    @Test
    fun `success resets consecutive failure count`() = testSuspend {
        var status = HttpStatusCode.InternalServerError
        val client = createTestClient(respondWith = { status }) {
            register("svc") {
                failureThreshold = 3
                resetTimeout = 30.seconds
            }
        }

        repeat(2) {
            client.get("http://svc/") { circuitBreaker("svc") }
        }

        status = HttpStatusCode.OK
        client.get("http://svc/") { circuitBreaker("svc") }

        status = HttpStatusCode.InternalServerError
        repeat(2) {
            client.get("http://svc/") { circuitBreaker("svc") }
        }

        status = HttpStatusCode.OK
        client.get("http://svc/") { circuitBreaker("svc") }

        client.close()
    }

    @Test
    fun `circuit transitions from open to half-open after reset timeout`() = testSuspend {
        val timeSource = TestTimeSource()
        var status = HttpStatusCode.InternalServerError

        val client = createTestClient(timeSource = timeSource, respondWith = { status }) {
            register("svc") {
                failureThreshold = 2
                resetTimeout = 30.seconds
                halfOpenRequests = 1
            }
        }

        repeat(2) {
            client.get("http://svc/") { circuitBreaker("svc") }
        }

        assertFailsWith<CircuitBreakerOpenException> {
            client.get("http://svc/") { circuitBreaker("svc") }
        }

        timeSource += 31.seconds
        status = HttpStatusCode.OK

        client.get("http://svc/") { circuitBreaker("svc") }

        client.close()
    }

    @Test
    fun `circuit closes after all half-open requests succeed`() = testSuspend {
        val timeSource = TestTimeSource()
        var status = HttpStatusCode.InternalServerError

        val client = createTestClient(timeSource = timeSource, respondWith = { status }) {
            register("svc") {
                failureThreshold = 2
                resetTimeout = 10.seconds
                halfOpenRequests = 3
            }
        }

        repeat(2) {
            client.get("http://svc/") { circuitBreaker("svc") }
        }

        timeSource += 11.seconds
        status = HttpStatusCode.OK

        repeat(3) {
            client.get("http://svc/") { circuitBreaker("svc") }
        }

        repeat(10) {
            client.get("http://svc/") { circuitBreaker("svc") }
        }

        client.close()
    }

    @Test
    fun `circuit re-opens when a half-open request fails`() = testSuspend {
        val timeSource = TestTimeSource()
        var status = HttpStatusCode.InternalServerError

        val client = createTestClient(timeSource = timeSource, respondWith = { status }) {
            register("svc") {
                failureThreshold = 2
                resetTimeout = 10.seconds
                halfOpenRequests = 3
            }
        }

        repeat(2) {
            client.get("http://svc/") { circuitBreaker("svc") }
        }

        timeSource += 11.seconds

        client.get("http://svc/") { circuitBreaker("svc") }

        assertFailsWith<CircuitBreakerOpenException> {
            client.get("http://svc/") { circuitBreaker("svc") }
        }

        client.close()
    }

    @Test
    fun `requests without circuit breaker tag bypass the plugin`() = testSuspend {
        val client = createTestClient(respondWith = { HttpStatusCode.InternalServerError }) {
            register("svc") {
                failureThreshold = 1
                resetTimeout = 30.seconds
            }
        }

        repeat(10) {
            val response = client.get("http://svc/")
            assertEquals(HttpStatusCode.InternalServerError, response.status)
        }

        client.close()
    }

    @Test
    fun `separate circuits operate independently`() = testSuspend {
        var svcAStatus = HttpStatusCode.InternalServerError
        var svcBStatus = HttpStatusCode.OK

        val client = HttpClient(MockEngine) {
            engine {
                addHandler { request ->
                    val status = if (request.url.host == "svc-a") svcAStatus else svcBStatus
                    respond("", status)
                }
            }
            install(CircuitBreaker) {
                register("svc-a") {
                    failureThreshold = 2
                    resetTimeout = 30.seconds
                }
                register("svc-b") {
                    failureThreshold = 2
                    resetTimeout = 30.seconds
                }
            }
        }

        repeat(2) {
            client.get("http://svc-a/") { circuitBreaker("svc-a") }
        }

        assertFailsWith<CircuitBreakerOpenException> {
            client.get("http://svc-a/") { circuitBreaker("svc-a") }
        }

        repeat(5) {
            client.get("http://svc-b/") { circuitBreaker("svc-b") }
        }

        client.close()
    }

    @Test
    fun `custom failure predicate determines failures`() = testSuspend {
        val client = createTestClient(respondWith = { HttpStatusCode.BadRequest }) {
            register("svc") {
                failureThreshold = 2
                resetTimeout = 30.seconds
                isFailure { response -> response.status.value >= 400 }
            }
        }

        repeat(2) {
            client.get("http://svc/") { circuitBreaker("svc") }
        }

        assertFailsWith<CircuitBreakerOpenException> {
            client.get("http://svc/") { circuitBreaker("svc") }
        }

        client.close()
    }

    @Test
    fun `custom request router assigns circuit breakers`() = testSuspend {
        var status = HttpStatusCode.InternalServerError

        val client = HttpClient(MockEngine) {
            engine {
                addHandler { respond("", status) }
            }
            install(CircuitBreaker) {
                routeRequests { request -> request.url.host }
                global {
                    failureThreshold = 2
                    resetTimeout = 30.seconds
                }
            }
        }

        repeat(2) {
            client.get("http://my-host/")
        }

        assertFailsWith<CircuitBreakerOpenException> {
            client.get("http://my-host/")
        }

        status = HttpStatusCode.OK
        client.get("http://other-host/")

        client.close()
    }

    @Test
    fun `explicit circuit breaker attribute takes priority over router`() = testSuspend {
        val timeSource = TestTimeSource()
        var status = HttpStatusCode.InternalServerError

        val client = HttpClient(MockEngine) {
            engine {
                addHandler { respond("", status) }
            }
            install(CircuitBreaker) {
                this.timeSource = timeSource
                routeRequests { request -> request.url.host }
                register("explicit") {
                    failureThreshold = 1
                    resetTimeout = 30.seconds
                }
                global {
                    failureThreshold = 100
                    resetTimeout = 30.seconds
                }
            }
        }

        client.get("http://my-host/") { circuitBreaker("explicit") }

        assertFailsWith<CircuitBreakerOpenException> {
            client.get("http://my-host/") { circuitBreaker("explicit") }
        }

        client.get("http://my-host/")

        client.close()
    }

    @Test
    fun `exception contains circuit breaker name and reset timeout`() = testSuspend {
        val client = createTestClient(respondWith = { HttpStatusCode.InternalServerError }) {
            register("payment-service") {
                failureThreshold = 1
                resetTimeout = 45.seconds
            }
        }

        client.get("http://svc/") { circuitBreaker("payment-service") }

        val exception = assertFailsWith<CircuitBreakerOpenException> {
            client.get("http://svc/") { circuitBreaker("payment-service") }
        }
        assertEquals("payment-service", exception.circuitBreakerName)
        assertEquals(45.seconds, exception.resetTimeout)
        assertContains(exception.message!!, "payment-service")

        client.close()
    }

    @Test
    fun `invalid configuration is rejected`() {
        assertFailsWith<IllegalArgumentException> {
            HttpClient(MockEngine) {
                engine { addHandler { respond("") } }
                install(CircuitBreaker) {
                    register("svc") { failureThreshold = 0 }
                }
            }
        }

        assertFailsWith<IllegalArgumentException> {
            HttpClient(MockEngine) {
                engine { addHandler { respond("") } }
                install(CircuitBreaker) {
                    register("svc") { halfOpenRequests = -1 }
                }
            }
        }

        assertFailsWith<IllegalArgumentException> {
            HttpClient(MockEngine) {
                engine { addHandler { respond("") } }
                install(CircuitBreaker) {
                    register("svc") { resetTimeout = Duration.ZERO }
                }
            }
        }
    }

    @Test
    fun `4xx responses do not trip the circuit by default`() = testSuspend {
        val client = createTestClient(respondWith = { HttpStatusCode.BadRequest }) {
            register("svc") {
                failureThreshold = 2
                resetTimeout = 30.seconds
            }
        }

        repeat(10) {
            client.get("http://svc/") { circuitBreaker("svc") }
        }

        client.close()
    }

    @Test
    fun `circuit breaker recovers after full cycle`() = testSuspend {
        val timeSource = TestTimeSource()
        var status = HttpStatusCode.InternalServerError

        val client = createTestClient(timeSource = timeSource, respondWith = { status }) {
            register("svc") {
                failureThreshold = 2
                resetTimeout = 10.seconds
                halfOpenRequests = 2
            }
        }

        repeat(2) {
            client.get("http://svc/") { circuitBreaker("svc") }
        }

        assertFailsWith<CircuitBreakerOpenException> {
            client.get("http://svc/") { circuitBreaker("svc") }
        }

        timeSource += 11.seconds
        status = HttpStatusCode.OK

        repeat(2) {
            client.get("http://svc/") { circuitBreaker("svc") }
        }

        repeat(5) {
            client.get("http://svc/") { circuitBreaker("svc") }
        }

        status = HttpStatusCode.InternalServerError
        repeat(2) {
            client.get("http://svc/") { circuitBreaker("svc") }
        }

        assertFailsWith<CircuitBreakerOpenException> {
            client.get("http://svc/") { circuitBreaker("svc") }
        }

        client.close()
    }

    private fun createTestClient(
        timeSource: TimeSource.WithComparableMarks = TimeSource.Monotonic,
        respondWith: () -> HttpStatusCode,
        configure: CircuitBreakerConfig.() -> Unit
    ): HttpClient {
        return HttpClient(MockEngine) {
            engine {
                addHandler { respond("", respondWith()) }
            }
            install(CircuitBreaker) {
                this.timeSource = timeSource
                configure()
            }
        }
    }
}
