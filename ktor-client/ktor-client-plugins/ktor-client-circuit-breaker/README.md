# ktor-client-circuit-breaker

A Ktor HTTP client plugin implementing the [Circuit Breaker](https://learn.microsoft.com/en-us/azure/architecture/patterns/circuit-breaker) pattern to prevent cascading failures in distributed systems.

## Motivation

The Ktor client ships with `HttpRequestRetry` and `HttpTimeout`, but has no built-in protection against repeatedly calling a failing downstream service. Without a circuit breaker, a single unhealthy dependency can exhaust connection pools, saturate thread pools, and cascade failures through the call graph. This plugin fills that gap.

## How it works

Each named circuit breaker is a state machine with three states:

```
         failures >= threshold
  ┌────────┐                  ┌──────┐
  │ CLOSED ├─────────────────►│ OPEN │
  └───┬────┘                  └──┬───┘
      │                          │ resetTimeout elapsed
      │  all trial requests      │
      │  succeed                 ▼
      │                    ┌───────────┐
      └────────────────────┤ HALF-OPEN │
                           └─────┬─────┘
                                 │ any trial request fails
                                 │
                                 ▼
                            ┌──────┐
                            │ OPEN │
                            └──────┘
```

- **Closed** -- Normal operation. Requests pass through. Consecutive failures are counted. A successful response resets the counter. When the counter reaches `failureThreshold`, the circuit trips to Open.
- **Open** -- The circuit is tripped. All requests are immediately rejected with `CircuitBreakerOpenException` without hitting the network. After `resetTimeout` elapses, the circuit transitions to Half-Open.
- **Half-Open** -- The circuit allows up to `halfOpenRequests` trial requests through. If all succeed, the circuit closes. If any fails, the circuit re-opens.

## Installation

Add the dependency (published alongside `ktor-client-core`):

```kotlin
dependencies {
    implementation("io.ktor:ktor-client-circuit-breaker:$ktor_version")
}
```

## Usage

### Basic configuration

```kotlin
val client = HttpClient(CIO) {
    install(CircuitBreaker) {
        register("payment-service") {
            failureThreshold = 5      // open after 5 consecutive failures
            resetTimeout = 30.seconds  // wait 30s before probing
            halfOpenRequests = 3       // allow 3 trial requests in half-open
        }
    }
}
```

Tag each request with the circuit breaker it belongs to:

```kotlin
val response = client.get("https://payment.example.com/api/charge") {
    circuitBreaker("payment-service")
}
```

### Multiple services

```kotlin
install(CircuitBreaker) {
    register("payment-service") {
        failureThreshold = 5
        resetTimeout = 30.seconds
    }
    register("inventory-service") {
        failureThreshold = 10
        resetTimeout = 1.minutes
    }
}
```

Each circuit is independent -- tripping `payment-service` does not affect `inventory-service`.

### Automatic routing by host

Instead of tagging every request manually, route by host:

```kotlin
install(CircuitBreaker) {
    routeRequests { request -> request.url.host }
    global {
        failureThreshold = 5
        resetTimeout = 30.seconds
    }
}
```

An explicit `circuitBreaker("name")` attribute on a request always takes priority over the router.

### Custom failure detection

By default, responses with status code >= 500 are treated as failures. Customize this per circuit:

```kotlin
register("strict-service") {
    failureThreshold = 3
    resetTimeout = 10.seconds
    isFailure { response ->
        response.status.value >= 400
    }
}
```

Exceptions thrown during the request (network errors, timeouts) always count as failures regardless of this predicate.

### Handling rejections

When the circuit is open, requests throw `CircuitBreakerOpenException`:

```kotlin
try {
    client.get("https://payment.example.com/api/charge") {
        circuitBreaker("payment-service")
    }
} catch (e: CircuitBreakerOpenException) {
    // Circuit is open -- return a fallback or cached response
    println("${e.circuitBreakerName} is unavailable (reset in ${e.resetTimeout})")
}
```

## Configuration reference

| Property | Default | Description |
|---|---|---|
| `failureThreshold` | `5` | Consecutive failures required to trip the circuit |
| `resetTimeout` | `60s` | Duration the circuit stays open before transitioning to half-open |
| `halfOpenRequests` | `3` | Number of trial requests in the half-open state |
| `isFailure { }` | `status >= 500` | Predicate to classify a response as a failure |

## Interaction with other plugins

- **HttpRequestRetry** -- Install `HttpRequestRetry` *before* `CircuitBreaker` so the circuit breaker wraps the retry logic. This way the circuit sees the final outcome after all retries, and `CircuitBreakerOpenException` is not retried.
- **HttpTimeout** -- Timeout exceptions are counted as circuit breaker failures.
