# ktor-client-opentelemetry

Native OpenTelemetry integration for Ktor HTTP client. Provides distributed tracing, metrics, and trace context injection for outgoing requests following [OpenTelemetry semantic conventions](https://opentelemetry.io/docs/specs/semconv/http/http-spans/).

## Installation

Add the dependency to your project:

```kotlin
dependencies {
    implementation("io.ktor:ktor-client-opentelemetry:$ktor_version")
}
```

## Usage

### Basic setup

```kotlin
import io.ktor.client.plugins.opentelemetry.*
import io.opentelemetry.sdk.OpenTelemetrySdk

val openTelemetry = OpenTelemetrySdk.builder()
    .setTracerProvider(sdkTracerProvider)
    .setPropagators(ContextPropagators.create(W3CTraceContextPropagator.getInstance()))
    .build()

val client = HttpClient(CIO) {
    install(OpenTelemetry) {
        openTelemetry = openTelemetry
    }
}
```

### Individual component configuration

```kotlin
val client = HttpClient(CIO) {
    install(OpenTelemetry) {
        tracerProvider = sdkTracerProvider
        meterProvider = sdkMeterProvider
        propagators = W3CTraceContextPropagator.getInstance()
    }
}
```

### Header capture

```kotlin
val client = HttpClient(CIO) {
    install(OpenTelemetry) {
        openTelemetry = otel
        captureRequestHeaders("X-Request-ID", "X-Correlation-ID")
        captureResponseHeaders("X-RateLimit-Remaining")
    }
}
```

### Filtering

```kotlin
val client = HttpClient(CIO) {
    install(OpenTelemetry) {
        openTelemetry = otel
        filter { request -> !request.url.encodedPath.startsWith("/internal") }
    }
}
```

## Features

### Client spans

Creates a `CLIENT` span for each outgoing HTTP request with attributes:

| Attribute | Description |
|---|---|
| `http.request.method` | HTTP method (GET, POST, etc.) |
| `server.address` | Target server hostname |
| `server.port` | Target server port |
| `url.full` | Full request URL |
| `http.response.status_code` | Response status code |
| `error.type` | Exception class name or HTTP status code (on error) |

### Trace context injection

Automatically injects trace context headers into outgoing requests using the configured `TextMapPropagator`. With W3C Trace Context (default), the `traceparent` and `tracestate` headers are added to every outgoing request.

### Server-client context propagation

When used together with [ktor-server-opentelemetry](../../../ktor-server/ktor-server-plugins/ktor-server-opentelemetry), trace context flows automatically from incoming server requests to outgoing client requests. The server plugin propagates the OTEL context through Kotlin coroutines, so client spans created inside a request handler automatically become children of the server span:

```kotlin
fun Application.module() {
    install(io.ktor.server.plugins.opentelemetry.OpenTelemetry) {
        openTelemetry = otel
    }

    val client = HttpClient(CIO) {
        install(io.ktor.client.plugins.opentelemetry.OpenTelemetry) {
            openTelemetry = otel
        }
    }

    routing {
        get("/api/users/{id}") {
            // The client span for this call is automatically a child of the server span
            val user = client.get("http://user-service/users/${call.parameters["id"]}")
            call.respond(user.body())
        }
    }
}
```

This produces a distributed trace:

```
[Server] GET /api/users/{id}
  └── [Client] HTTP GET  →  user-service
```

### Metrics

| Metric | Type | Description |
|---|---|---|
| `http.client.request.duration` | Histogram (seconds) | Duration of HTTP client requests |

Metric attributes include `http.request.method`, `http.response.status_code`, and `server.address`.

### Error handling

- Network errors and exceptions are recorded on the span via `Span.recordException()`
- HTTP 4xx/5xx responses set the span status to `ERROR`
- The `error.type` attribute is set to the exception class name or HTTP status code
- Spans are always ended, even when exceptions occur, preventing span leaks
