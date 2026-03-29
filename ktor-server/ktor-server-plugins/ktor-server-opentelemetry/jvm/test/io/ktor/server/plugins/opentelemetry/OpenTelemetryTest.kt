/*
 * Copyright 2014-2025 JetBrains s.r.o and contributors. Use of this source code is governed by the Apache 2.0 license.
 */

package io.ktor.server.plugins.opentelemetry

import io.ktor.client.request.*
import io.ktor.http.*
import io.ktor.server.request.*
import io.ktor.server.response.*
import io.ktor.server.routing.*
import io.ktor.server.testing.*
import io.opentelemetry.api.trace.*
import io.opentelemetry.api.trace.propagation.*
import io.opentelemetry.context.propagation.*
import io.opentelemetry.sdk.*
import io.opentelemetry.sdk.testing.exporter.*
import io.opentelemetry.sdk.trace.*
import io.opentelemetry.sdk.trace.export.*
import kotlin.test.*

class OpenTelemetryTest {

    private fun createTestOtel(spanExporter: InMemorySpanExporter): io.opentelemetry.api.OpenTelemetry {
        val tracerProvider = SdkTracerProvider.builder()
            .addSpanProcessor(SimpleSpanProcessor.create(spanExporter))
            .build()
        return OpenTelemetrySdk.builder()
            .setTracerProvider(tracerProvider)
            .setPropagators(ContextPropagators.create(W3CTraceContextPropagator.getInstance()))
            .build()
    }

    @Test
    fun `spans are created for requests`() = testApplication {
        val spanExporter = InMemorySpanExporter.create()

        install(OpenTelemetry) {
            openTelemetry = createTestOtel(spanExporter)
        }

        routing {
            get("/test") {
                call.respondText("hello")
            }
        }

        client.get("/test")

        val spans = spanExporter.finishedSpanItems
        assertEquals(1, spans.size)

        val span = spans.first()
        assertEquals(SpanKind.SERVER, span.kind)
        assertEquals("GET /test", span.name)

        val method = span.attributes.get(io.opentelemetry.api.common.AttributeKey.stringKey("http.request.method"))
        assertEquals("GET", method)

        val statusCode = span.attributes.get(
            io.opentelemetry.api.common.AttributeKey.longKey("http.response.status_code")
        )
        assertEquals(200L, statusCode)
    }

    @Test
    fun `span name is updated with route template`() = testApplication {
        val spanExporter = InMemorySpanExporter.create()

        install(OpenTelemetry) {
            openTelemetry = createTestOtel(spanExporter)
        }

        routing {
            get("/users/{id}") {
                call.respondText("user")
            }
        }

        client.get("/users/42")

        val spans = spanExporter.finishedSpanItems
        assertEquals(1, spans.size)
        assertEquals("GET /users/{id}", spans.first().name)

        val route = spans.first().attributes.get(io.opentelemetry.api.common.AttributeKey.stringKey("http.route"))
        assertEquals("/users/{id}", route)
    }

    @Test
    fun `error status is recorded on spans`() = testApplication {
        val spanExporter = InMemorySpanExporter.create()

        install(OpenTelemetry) {
            openTelemetry = createTestOtel(spanExporter)
        }

        routing {
            get("/error") {
                call.respond(HttpStatusCode.InternalServerError, "error")
            }
        }

        client.get("/error")

        val spans = spanExporter.finishedSpanItems
        assertEquals(1, spans.size)

        val span = spans.first()
        assertEquals(StatusCode.ERROR, span.status.statusCode)

        val statusCode = span.attributes.get(
            io.opentelemetry.api.common.AttributeKey.longKey("http.response.status_code")
        )
        assertEquals(500L, statusCode)
    }

    @Test
    fun `request headers are captured as span attributes`() = testApplication {
        val spanExporter = InMemorySpanExporter.create()

        install(OpenTelemetry) {
            openTelemetry = createTestOtel(spanExporter)
            captureRequestHeaders("X-Request-ID")
        }

        routing {
            get("/test") {
                call.respondText("ok")
            }
        }

        client.get("/test") {
            header("X-Request-ID", "req-123")
        }

        val spans = spanExporter.finishedSpanItems
        assertEquals(1, spans.size)

        val headerValues = spans.first().attributes.get(
            io.opentelemetry.api.common.AttributeKey.stringArrayKey("http.request.header.x-request-id")
        )
        assertNotNull(headerValues)
        assertEquals(listOf("req-123"), headerValues)
    }

    @Test
    fun `incoming trace context is propagated`() = testApplication {
        val spanExporter = InMemorySpanExporter.create()

        install(OpenTelemetry) {
            openTelemetry = createTestOtel(spanExporter)
        }

        routing {
            get("/test") {
                call.respondText("ok")
            }
        }

        val traceId = "0af7651916cd43dd8448eb211c80319c"
        val parentSpanId = "b7ad6b7169203331"

        client.get("/test") {
            header("traceparent", "00-$traceId-$parentSpanId-01")
        }

        val spans = spanExporter.finishedSpanItems
        assertEquals(1, spans.size)

        val span = spans.first()
        assertEquals(traceId, span.spanContext.traceId)
        assertEquals(parentSpanId, span.parentSpanId)
    }

    @Test
    fun `filtered requests are not traced`() = testApplication {
        val spanExporter = InMemorySpanExporter.create()

        install(OpenTelemetry) {
            openTelemetry = createTestOtel(spanExporter)
            filter { it.request.path().startsWith("/api") }
        }

        routing {
            get("/health") {
                call.respondText("ok")
            }
            get("/api") {
                call.respondText("data")
            }
        }

        client.get("/health")
        client.get("/api")

        val spans = spanExporter.finishedSpanItems
        assertEquals(1, spans.size)
        assertEquals("GET /api", spans.first().name)
    }

    @Test
    fun `custom route transformation is applied`() = testApplication {
        val spanExporter = InMemorySpanExporter.create()

        install(OpenTelemetry) {
            openTelemetry = createTestOtel(spanExporter)
            transformRoute { "/api${it.path}" }
        }

        routing {
            get("/users") {
                call.respondText("users")
            }
        }

        client.get("/users")

        val spans = spanExporter.finishedSpanItems
        assertEquals(1, spans.size)

        val route = spans.first().attributes.get(io.opentelemetry.api.common.AttributeKey.stringKey("http.route"))
        assertEquals("/api/users", route)
    }

    @Test
    fun `custom http method requests are not traced`() = testApplication {
        val spanExporter = InMemorySpanExporter.create()

        install(OpenTelemetry) {
            openTelemetry = createTestOtel(spanExporter)
        }

        routing {
            get("/test") {
                call.respondText("hello")
            }
        }

        client.get("/test")
        assertEquals(1, spanExporter.finishedSpanItems.size)

        client.request("/test") {
            method = HttpMethod("CUSTOM")
        }
        assertEquals(1, spanExporter.finishedSpanItems.size, "Custom methods should not be traced")
    }
}
