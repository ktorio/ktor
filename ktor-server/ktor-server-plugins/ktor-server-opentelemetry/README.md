# ktor-server-opentelemetry

Native OpenTelemetry integration for Ktor server applications. Provides distributed tracing, metrics, and trace context propagation following [OpenTelemetry semantic conventions](https://opentelemetry.io/docs/specs/semconv/http/http-spans/).

## Installation

Add the dependency to your project:

```kotlin
dependencies {
    implementation("io.ktor:ktor-server-opentelemetry:$ktor_version")
}
```

## Usage

### Basic setup

```kotlin
import io.ktor.server.plugins.opentelemetry.*
import io.opentelemetry.sdk.OpenTelemetrySdk
import io.opentelemetry.sdk.trace.SdkTracerProvider

val openTelemetry = OpenTelemetrySdk.builder()
    .setTracerProvider(sdkTracerProvider)
    .setPropagators(ContextPropagators.create(W3CTraceContextPropagator.getInstance()))
    .build()

fun Application.module() {
    install(OpenTelemetry) {
        openTelemetry = openTelemetry
    }
}
```

### Individual component configuration

Instead of passing a full `OpenTelemetry` instance, you can set components individually:

```kotlin
install(OpenTelemetry) {
    tracerProvider = sdkTracerProvider
    meterProvider = sdkMeterProvider
    propagators = W3CTraceContextPropagator.getInstance()
}
```

### Header capture

Capture request and response headers as span attributes:

```kotlin
install(OpenTelemetry) {
    openTelemetry = otel
    captureRequestHeaders("X-Request-ID", "X-Correlation-ID")
    captureResponseHeaders("X-Response-Time")
}
```

### Filtering

Exclude specific requests from instrumentation:

```kotlin
install(OpenTelemetry) {
    openTelemetry = otel
    filter { call -> !call.request.path().startsWith("/health") }
}
```

### Route transformation

Customize how route templates appear in spans:

```kotlin
install(OpenTelemetry) {
    openTelemetry = otel
    transformRoute { node -> "/api/v1${node.path}" }
}
```

## Features

### Server spans

Creates a `SERVER` span for each incoming HTTP request with attributes:

| Attribute | Description |
|---|---|
| `http.request.method` | HTTP method (GET, POST, etc.) |
| `url.path` | Request path |
| `url.scheme` | URL scheme (http/https) |
| `server.address` | Server hostname |
| `server.port` | Server port |
| `network.protocol.version` | HTTP version (1.1, 2, etc.) |
| `user_agent.original` | User-Agent header value |
| `http.route` | Resolved route template (e.g., `/users/{id}`) |
| `http.response.status_code` | Response status code |
| `error.type` | Exception class name (on error) |

Span names are automatically updated from the initial `GET /users/42` to `GET /users/{id}` when routing resolves the matched route template.

### Trace context propagation

Extracts incoming trace context from request headers using the configured `TextMapPropagator` (W3C Trace Context by default). The extracted context is set as the parent of the server span, enabling distributed traces across services.

The OTEL context is propagated through Kotlin coroutines via a `ThreadContextElement`, so `Context.current()` returns the correct context inside request handlers. This enables automatic parent span detection when using the [ktor-client-opentelemetry](../../ktor-client/ktor-client-plugins/ktor-client-opentelemetry) plugin for outgoing requests.

### Metrics

Records the following metrics:

| Metric | Type | Description |
|---|---|---|
| `http.server.request.duration` | Histogram (seconds) | Duration of HTTP server requests |
| `http.server.active_requests` | UpDownCounter | Number of active HTTP server requests |

Metric attributes include `http.request.method`, `http.response.status_code`, `http.route`, and `url.scheme`.

### Error recording

Exceptions thrown during request processing are recorded on the span:
- Span status is set to `ERROR` for 5xx responses or unhandled exceptions
- The exception is recorded via `Span.recordException()`
- The `error.type` attribute is set to the exception class name
