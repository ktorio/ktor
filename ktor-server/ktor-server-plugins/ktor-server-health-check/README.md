# ktor-server-health-check

A Ktor server plugin that provides health check endpoints for Kubernetes readiness and liveness probes (and any other orchestration system that polls HTTP health endpoints).

## Motivation

Every production Ktor application deployed to Kubernetes needs health and readiness endpoints. Currently, developers write these manually every time. This plugin provides a declarative DSL - similar to Spring Actuator, Micronaut Health, or Quarkus SmallRye Health - so that health endpoints are consistent, correct, and require minimal boilerplate.

## Installation

Add the dependency to your `build.gradle.kts`:

```kotlin
dependencies {
    implementation("io.ktor:ktor-server-health-check:$ktor_version")
}
```

## Quick Start

```kotlin
import io.ktor.server.application.*
import io.ktor.server.plugins.healthcheck.*

fun Application.module() {
    install(HealthCheck) {
        readiness("/ready") {
            check("database") { dataSource.connection.isValid(1) }
            check("redis") { redisClient.ping(); true }
        }
        liveness("/health") {
            check("memory") { Runtime.getRuntime().freeMemory() > 10_000_000 }
        }
    }
}
```

## DSL Reference

### `readiness(path) { ... }`

Configures a readiness endpoint. Readiness checks determine whether the application is ready to serve traffic. Failed readiness checks cause Kubernetes to stop routing requests to the pod.

### `liveness(path) { ... }`

Configures a liveness endpoint. Liveness checks determine whether the application is still running. Failed liveness checks cause Kubernetes to restart the container.

### `check(name) { ... }`

Adds a named health check within a readiness or liveness block. The lambda should return `true` for healthy, `false` for unhealthy. If the lambda throws an exception, the check is treated as unhealthy and the exception message is included in the response.

## Response Format

The plugin responds with `application/json` and uses standard HTTP status codes:

| Condition | HTTP Status | `status` field |
|---|---|---|
| All checks pass | 200 OK | `UP` |
| Any check fails | 503 Service Unavailable | `DOWN` |

### All healthy

```json
{
  "status": "UP",
  "checks": [
    { "name": "database", "status": "UP" },
    { "name": "redis", "status": "UP" }
  ]
}
```

### One or more unhealthy

```json
{
  "status": "DOWN",
  "checks": [
    { "name": "database", "status": "UP" },
    { "name": "redis", "status": "DOWN", "error": "Connection refused" }
  ]
}
```

## Kubernetes Configuration

```yaml
apiVersion: apps/v1
kind: Deployment
spec:
  template:
    spec:
      containers:
        - name: app
          livenessProbe:
            httpGet:
              path: /health
              port: 8080
            initialDelaySeconds: 10
            periodSeconds: 15
          readinessProbe:
            httpGet:
              path: /ready
              port: 8080
            initialDelaySeconds: 5
            periodSeconds: 10
```

## Design Decisions

- **Concurrent checks**: All checks within an endpoint run concurrently via `coroutineScope` + `async`, minimizing probe latency when multiple I/O-bound checks are configured.
- **No external dependencies**: JSON responses are built with `StringBuilder` - no serialization library required. The plugin does not require `ContentNegotiation`.
- **`onCall` interception**: The plugin intercepts at the call level (before routing), matching configured paths directly. This means health endpoints work even without installing `Routing`.
- **GET-only**: Only `GET` requests are handled, matching the HTTP method used by Kubernetes probes and load balancers.
- **Multiplatform**: The plugin is implemented in `commonMain` and works on all Ktor server targets (JVM, Native, JS).
