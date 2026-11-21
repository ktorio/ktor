/*
 * Copyright 2014-2026 JetBrains s.r.o and contributors. Use of this source code is governed by the Apache 2.0 license.
 */

package io.ktor.server.plugins.exceptionboundaries

import io.ktor.client.request.*
import io.ktor.client.statement.*
import io.ktor.http.*
import io.ktor.server.plugins.*
import io.ktor.server.request.*
import io.ktor.server.response.*
import io.ktor.server.routing.*
import io.ktor.server.testing.*
import kotlinx.coroutines.*
import kotlin.test.*
import kotlin.time.Duration.Companion.seconds

/**
 * A small shop application that uses exception boundaries the way an application would.
 *
 * Every test sends one request and checks what the client sees, or what the alerts recorded.
 * Change the handlers in [shop] and see which tests break to explore how the boundaries behave.
 */
class ExceptionBoundariesUserJourneyTest {

    // The exceptions of the application's own layers

    private open class DatabaseException(message: String) : RuntimeException(message)
    private class DatabaseUnavailable : DatabaseException("connection pool is exhausted")
    private class ProductNotFound(val id: String) : RuntimeException("product $id is not found")
    private class PaymentDeclined(val reason: String) : RuntimeException("payment is declined: $reason")
    private class AdminOnly : RuntimeException("admins only")

    /** Records what the coroutine handlers reported, in place of a real alerting system. */
    private class Alerts {
        val reported = CompletableDeferred<String>()
    }

    // Handlers are values, so they can live next to the code whose exceptions they handle

    private val databaseFailures = exceptionHandler {
        on<DatabaseUnavailable> {
            call.response.headers.append(HttpHeaders.RetryAfter, "5")
            call.respond(HttpStatusCode.ServiceUnavailable)
        }
        on<DatabaseException> { call.respond(HttpStatusCode.InternalServerError) }
    }

    private val catalogFailures = exceptionHandler {
        on<ProductNotFound> { e -> call.respondText("No product ${e.id}", status = HttpStatusCode.NotFound) }
    }

    private val checkoutFailures = exceptionHandler {
        on<PaymentDeclined> { e -> call.respondText(e.reason, status = HttpStatusCode.PaymentRequired) }
    }

    private fun ApplicationTestBuilder.shop(alerts: Alerts = Alerts()) {
        application {
            // Application-wide: any call, and coroutines that don't belong to a call
            globalExceptionBoundary {
                on<AdminOnly> { call.respond(HttpStatusCode.Forbidden) }
                onApplicationCoroutineFailure<DatabaseException> { e, context ->
                    alerts.reported.complete("${context[CoroutineName]?.name}: ${e.message}")
                }
            }

            routing {
                // Every route below talks to the database
                exceptionBoundary(databaseFailures) {
                    exceptionBoundary(catalogFailures) {
                        get("/products/{id}") {
                            when (val id = call.parameters["id"]!!) {
                                "down" -> throw DatabaseUnavailable()
                                "broken" -> throw DatabaseException("constraint violated")
                                "42" -> call.respondText("The answer")
                                else -> throw ProductNotFound(id)
                            }
                        }

                        get("/products/{id}/recommendations") {
                            // call.launch starts a child of the whole call, not of this route, so its failure
                            // skips the catalog and database boundaries. Treat it as a bug, not an expected error.
                            val warmUp = call.launch { throw DatabaseUnavailable() }
                            warmUp.join()
                            call.respondText("Recommended")
                        }
                    }

                    // The checkout boundary also reports failures of the background work of a call
                    val checkoutWithAudit = exceptionHandler {
                        onCallCoroutineFailure<DatabaseException> { e, _ ->
                            alerts.reported.complete("audit: ${e.message}")
                        }
                    }
                    exceptionBoundary(checkoutFailures, checkoutWithAudit) {
                        post("/checkout") {
                            val card = call.receiveText()
                            if (card == "expired") throw PaymentDeclined("card expired")

                            supervisorScope {
                                // The audit log may fail without failing the checkout
                                launch { throw DatabaseException("audit log is full") }
                            }
                            call.respondText("Paid")
                        }
                    }
                }

                get("/admin") { throw AdminOnly() }

                get("/reports/nightly") {
                    // Work that outlives the call belongs to the application
                    application.launch(CoroutineName("nightly-report")) {
                        throw DatabaseUnavailable()
                    }
                    call.respond(HttpStatusCode.Accepted)
                }

                get("/bug") { error("a bug with internal details") }
                get("/bad-request") { throw BadRequestException("id must be a number") }
            }
        }
    }

    @Test
    fun `a product is found`() = testApplication {
        shop()
        assertEquals("The answer", client.get("/products/42").bodyAsText())
    }

    @Test
    fun `a missing product is handled by the catalog boundary`() = testApplication {
        shop()
        val response = client.get("/products/7")
        assertEquals(HttpStatusCode.NotFound, response.status)
        assertEquals("No product 7", response.bodyAsText())
    }

    @Test
    fun `a database outage is handled by the outer database boundary`() = testApplication {
        shop()
        val response = client.get("/products/down")
        assertEquals(HttpStatusCode.ServiceUnavailable, response.status)
        assertEquals("5", response.headers[HttpHeaders.RetryAfter])
    }

    @Test
    fun `a failed child of the call is not handled by the boundaries`() = testApplication {
        shop()
        // The child fails the coroutine of the whole call, outside every scoped boundary, so the database boundary
        // never sees it. In testApplication no boundary gets it, so the test receives the exception.
        // On a running server it reaches the global boundary, which logs it and responds with a 500 here.
        assertFailsWith<DatabaseUnavailable> {
            client.get("/products/42/recommendations")
        }
    }

    @Test
    fun `a general database failure falls back to the general handler`() = testApplication {
        shop()
        assertEquals(HttpStatusCode.InternalServerError, client.get("/products/broken").status)
    }

    @Test
    fun `a declined payment is handled by the checkout boundary`() = testApplication {
        shop()
        val response = client.post("/checkout") { setBody("expired") }
        assertEquals(HttpStatusCode.PaymentRequired, response.status)
        assertEquals("card expired", response.bodyAsText())
    }

    @Test
    fun `a failed audit doesn't fail the checkout and is reported`() = testApplication {
        val alerts = Alerts()
        shop(alerts)
        assertEquals("Paid", client.post("/checkout") { setBody("valid") }.bodyAsText())
        assertEquals("audit: audit log is full", withTimeout(5.seconds) { alerts.reported.await() })
    }

    @Test
    fun `an exception outside every scoped boundary is handled by the global boundary`() = testApplication {
        shop()
        assertEquals(HttpStatusCode.Forbidden, client.get("/admin").status)
    }

    @Test
    fun `a failed background job is reported by the global boundary`() = testApplication {
        val alerts = Alerts()
        shop(alerts)
        assertEquals(HttpStatusCode.Accepted, client.get("/reports/nightly").status)
        assertEquals(
            "nightly-report: connection pool is exhausted",
            withTimeout(5.seconds) { alerts.reported.await() }
        )
    }

    @Test
    fun `an unexpected bug doesn't leak its message`() = testApplication {
        shop()
        val response = client.get("/bug")
        assertEquals(HttpStatusCode.InternalServerError, response.status)
        assertEquals("", response.bodyAsText())
    }

    @Test
    fun `a known Ktor exception keeps its default response`() = testApplication {
        shop()
        val response = client.get("/bad-request")
        assertEquals(HttpStatusCode.BadRequest, response.status)
        assertEquals("id must be a number", response.bodyAsText())
    }
}
