/*
 * Copyright 2014-2025 JetBrains s.r.o and contributors. Use of this source code is governed by the Apache 2.0 license.
 */

package io.ktor.client.plugins.opentelemetry

import io.ktor.client.request.*
import io.ktor.client.statement.*
import io.ktor.http.*
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

class OpenTelemetryClientTest {

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
    fun `spans are created for outgoing requests`() = testApplication {
        routing {
            get("/test") {
                call.respondText("hello")
            }
        }

        val spanExporter = InMemorySpanExporter.create()
        val otel = createTestOtel(spanExporter)

        val testClient = createClient {
            install(OpenTelemetry) {
                openTelemetry = otel
            }
        }

        testClient.get("/test")

        val spans = spanExporter.finishedSpanItems
        assertEquals(1, spans.size)

        val span = spans.first()
        assertEquals(SpanKind.CLIENT, span.kind)
        assertEquals("HTTP GET", span.name)

        val statusCode = span.attributes.get(
            io.opentelemetry.api.common.AttributeKey.longKey("http.response.status_code")
        )
        assertEquals(200L, statusCode)
    }

    @Test
    fun `trace context headers are injected`() = testApplication {
        var receivedTraceParent: String? = null

        routing {
            get("/test") {
                receivedTraceParent = call.request.headers["traceparent"]
                call.respondText("hello")
            }
        }

        val spanExporter = InMemorySpanExporter.create()
        val otel = createTestOtel(spanExporter)

        val testClient = createClient {
            install(OpenTelemetry) {
                openTelemetry = otel
            }
        }

        testClient.get("/test")

        assertNotNull(receivedTraceParent, "traceparent header should be injected")
        assertTrue(receivedTraceParent!!.startsWith("00-"), "traceparent should follow W3C format")
    }

    @Test
    fun `error status codes are recorded`() = testApplication {
        routing {
            get("/not-found") {
                call.respond(HttpStatusCode.NotFound, "not found")
            }
        }

        val spanExporter = InMemorySpanExporter.create()
        val otel = createTestOtel(spanExporter)

        val testClient = createClient {
            install(OpenTelemetry) {
                openTelemetry = otel
            }
        }

        testClient.get("/not-found")

        val spans = spanExporter.finishedSpanItems
        assertEquals(1, spans.size)

        val span = spans.first()
        assertEquals(StatusCode.ERROR, span.status.statusCode)

        val statusCode = span.attributes.get(
            io.opentelemetry.api.common.AttributeKey.longKey("http.response.status_code")
        )
        assertEquals(404L, statusCode)
    }

    @Test
    fun `filtered requests are not traced`() = testApplication {
        routing {
            get("/health") {
                call.respondText("ok")
            }
            get("/api") {
                call.respondText("data")
            }
        }

        val spanExporter = InMemorySpanExporter.create()
        val otel = createTestOtel(spanExporter)

        val testClient = createClient {
            install(OpenTelemetry) {
                openTelemetry = otel
                filter { request -> !request.url.encodedPath.startsWith("/health") }
            }
        }

        testClient.get("/health")
        testClient.get("/api")

        val spans = spanExporter.finishedSpanItems
        assertEquals(1, spans.size)
        assertEquals("HTTP GET", spans.first().name)
    }

    @Test
    fun `request headers are captured as span attributes`() = testApplication {
        routing {
            get("/test") {
                call.respondText("ok")
            }
        }

        val spanExporter = InMemorySpanExporter.create()
        val otel = createTestOtel(spanExporter)

        val testClient = createClient {
            install(OpenTelemetry) {
                openTelemetry = otel
                captureRequestHeaders("X-Custom-Header")
            }
        }

        testClient.get("/test") {
            header("X-Custom-Header", "custom-value")
        }

        val spans = spanExporter.finishedSpanItems
        assertEquals(1, spans.size)

        val headerValues = spans.first().attributes.get(
            io.opentelemetry.api.common.AttributeKey.stringArrayKey("http.request.header.x-custom-header")
        )
        assertNotNull(headerValues)
        assertEquals(listOf("custom-value"), headerValues)
    }

    @Test
    fun `server address attributes are set`() = testApplication {
        routing {
            get("/test") {
                call.respondText("ok")
            }
        }

        val spanExporter = InMemorySpanExporter.create()
        val otel = createTestOtel(spanExporter)

        val testClient = createClient {
            install(OpenTelemetry) {
                openTelemetry = otel
            }
        }

        testClient.get("/test")

        val spans = spanExporter.finishedSpanItems
        assertEquals(1, spans.size)

        val span = spans.first()
        val serverAddress = span.attributes.get(io.opentelemetry.api.common.AttributeKey.stringKey("server.address"))
        assertNotNull(serverAddress)

        val method = span.attributes.get(io.opentelemetry.api.common.AttributeKey.stringKey("http.request.method"))
        assertEquals("GET", method)
    }
}
