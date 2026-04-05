/*
 * Copyright 2014-2025 JetBrains s.r.o and contributors. Use of this source code is governed by the Apache 2.0 license.
 */

package io.ktor.client.plugins.circuitbreaker

import io.ktor.client.plugins.api.*
import io.ktor.client.request.*
import io.ktor.client.statement.*
import io.ktor.util.*
import io.ktor.util.logging.*
import io.ktor.utils.io.*
import kotlinx.coroutines.sync.*
import kotlin.time.*
import kotlin.time.Duration.Companion.seconds

private val LOGGER = KtorSimpleLogger("io.ktor.client.plugins.circuitbreaker.CircuitBreaker")

/**
 * Configuration for an individual service circuit breaker.
 *
 * The circuit breaker operates as a state machine with three states:
 * - **Closed**: Requests flow normally. Consecutive failures are tracked.
 * - **Open**: All requests are rejected with [CircuitBreakerOpenException].
 * - **Half-Open**: A limited number of trial requests are allowed through to probe service health.
 */
@KtorDsl
public class ServiceCircuitBreakerConfig {
    /**
     * Number of consecutive failures required to trip the circuit from Closed to Open.
     */
    public var failureThreshold: Int = 5

    /**
     * Duration the circuit remains in the Open state before transitioning to Half-Open.
     */
    public var resetTimeout: Duration = 60.seconds

    /**
     * Number of trial requests allowed in the Half-Open state.
     * If all trial requests succeed, the circuit closes.
     * If any trial request fails, the circuit re-opens.
     */
    public var halfOpenRequests: Int = 3

    internal var failurePredicate: (HttpResponse) -> Boolean = { it.status.value >= 500 }

    /**
     * Defines a custom predicate to classify a response as a failure.
     * By default, any response with a status code >= 500 is treated as a failure.
     *
     * ```kotlin
     * register("payment-service") {
     *     isFailure { response ->
     *         response.status.value >= 400
     *     }
     * }
     * ```
     */
    public fun isFailure(predicate: (HttpResponse) -> Boolean) {
        failurePredicate = predicate
    }

    internal fun validate() {
        require(failureThreshold > 0) { "failureThreshold must be positive, got $failureThreshold" }
        require(halfOpenRequests > 0) { "halfOpenRequests must be positive, got $halfOpenRequests" }
        require(resetTimeout.isPositive()) { "resetTimeout must be positive, got $resetTimeout" }
    }
}

/**
 * Configuration for the [CircuitBreaker] client plugin.
 *
 * Use [register] to define named circuit breakers for different services:
 * ```kotlin
 * install(CircuitBreaker) {
 *     register("payment-service") {
 *         failureThreshold = 5
 *         resetTimeout = 30.seconds
 *         halfOpenRequests = 3
 *     }
 *     register("inventory-service") {
 *         failureThreshold = 10
 *         resetTimeout = 1.minutes
 *     }
 * }
 * ```
 *
 * Tag individual requests with a circuit breaker name using [circuitBreaker]:
 * ```kotlin
 * client.get("https://api.example.com/pay") {
 *     circuitBreaker("payment-service")
 * }
 * ```
 */
@KtorDsl
public class CircuitBreakerConfig {
    internal val circuits: MutableMap<String, ServiceCircuitBreakerConfig> = mutableMapOf()
    internal var globalConfig: ServiceCircuitBreakerConfig = ServiceCircuitBreakerConfig()
    internal var requestRouter: ((HttpRequestBuilder) -> String?)? = null
    internal var timeSource: TimeSource.WithComparableMarks = TimeSource.Monotonic

    /**
     * Registers a named circuit breaker with custom configuration.
     *
     * @param name unique identifier for this circuit breaker, used to associate requests via [circuitBreaker]
     * @param block configuration block for this circuit breaker's behavior
     */
    public fun register(name: String, block: ServiceCircuitBreakerConfig.() -> Unit) {
        circuits[name] = ServiceCircuitBreakerConfig().apply(block)
    }

    /**
     * Configures default settings for circuit breakers that are created dynamically
     * (for example, via [routeRequests] for names not explicitly [register]ed).
     */
    public fun global(block: ServiceCircuitBreakerConfig.() -> Unit) {
        globalConfig.apply(block)
    }

    /**
     * Installs a request router that automatically determines which circuit breaker to apply
     * to each request. The router receives the [HttpRequestBuilder] and returns the circuit
     * breaker name, or `null` to skip circuit breaking.
     *
     * An explicit [circuitBreaker] attribute on a request takes priority over this router.
     *
     * ```kotlin
     * install(CircuitBreaker) {
     *     routeRequests { request -> request.url.host }
     * }
     * ```
     */
    public fun routeRequests(router: (HttpRequestBuilder) -> String?) {
        requestRouter = router
    }
}

internal enum class CircuitState {
    CLOSED,
    OPEN,
    HALF_OPEN
}

internal class CircuitBreakerInstance(
    private val name: String,
    private val config: ServiceCircuitBreakerConfig,
    private val timeSource: TimeSource.WithComparableMarks
) {
    private val mutex = Mutex()
    private var state: CircuitState = CircuitState.CLOSED
    private var consecutiveFailures: Int = 0
    private var halfOpenSuccesses: Int = 0
    private var halfOpenAttempts: Int = 0
    private var lastOpenedMark: ComparableTimeMark? = null

    suspend fun acquirePermission() {
        mutex.withLock {
            when (state) {
                CircuitState.CLOSED -> {}

                CircuitState.OPEN -> {
                    val mark = lastOpenedMark
                    if (mark != null && mark.elapsedNow() >= config.resetTimeout) {
                        LOGGER.trace("Circuit breaker '$name': OPEN -> HALF_OPEN (reset timeout elapsed)")
                        transitionTo(CircuitState.HALF_OPEN)
                        halfOpenAttempts++
                    } else {
                        throw CircuitBreakerOpenException(name, config.resetTimeout)
                    }
                }

                CircuitState.HALF_OPEN -> {
                    if (halfOpenAttempts >= config.halfOpenRequests) {
                        throw CircuitBreakerHalfOpenSaturatedException(
                            name,
                            config.resetTimeout,
                            halfOpenAttempts,
                            config.halfOpenRequests
                        )
                    }
                    halfOpenAttempts++
                }
            }
        }
    }

    suspend fun recordSuccess() {
        mutex.withLock {
            when (state) {
                CircuitState.CLOSED -> consecutiveFailures = 0

                CircuitState.HALF_OPEN -> {
                    halfOpenSuccesses++
                    if (halfOpenSuccesses >= config.halfOpenRequests) {
                        LOGGER.trace(
                            "Circuit breaker '$name': HALF_OPEN -> CLOSED (all trial requests succeeded)"
                        )
                        transitionTo(CircuitState.CLOSED)
                    }
                }

                CircuitState.OPEN -> {}
            }
        }
    }

    suspend fun recordFailure() {
        mutex.withLock {
            when (state) {
                CircuitState.CLOSED -> {
                    consecutiveFailures++
                    LOGGER.trace(
                        "Circuit breaker '$name': failure $consecutiveFailures/${config.failureThreshold}"
                    )
                    if (consecutiveFailures >= config.failureThreshold) {
                        LOGGER.trace("Circuit breaker '$name': CLOSED -> OPEN (failure threshold reached)")
                        transitionTo(CircuitState.OPEN)
                    }
                }

                CircuitState.HALF_OPEN -> {
                    LOGGER.trace("Circuit breaker '$name': HALF_OPEN -> OPEN (trial request failed)")
                    transitionTo(CircuitState.OPEN)
                }

                CircuitState.OPEN -> {}
            }
        }
    }

    private fun transitionTo(newState: CircuitState) {
        state = newState
        when (newState) {
            CircuitState.CLOSED -> {
                consecutiveFailures = 0
                halfOpenSuccesses = 0
                halfOpenAttempts = 0
                lastOpenedMark = null
            }

            CircuitState.OPEN -> {
                lastOpenedMark = timeSource.markNow()
                halfOpenSuccesses = 0
                halfOpenAttempts = 0
            }

            CircuitState.HALF_OPEN -> {
                halfOpenSuccesses = 0
                halfOpenAttempts = 0
            }
        }
    }
}

/**
 * Thrown when a request is rejected because the associated circuit breaker is in the Open state.
 *
 * @property circuitBreakerName the name of the circuit breaker that rejected the request
 * @property resetTimeout the configured duration the circuit stays open before allowing trial requests
 */
public class CircuitBreakerOpenException(
    public val circuitBreakerName: String,
    public val resetTimeout: Duration
) : IllegalStateException(
    "Circuit breaker '$circuitBreakerName' is OPEN. " +
        "Requests are rejected until the reset timeout ($resetTimeout) elapses."
)

/**
 * Thrown when a request is rejected because the associated circuit breaker is HALF_OPEN
 * and has already consumed all allowed trial requests.
 *
 * @property circuitBreakerName the name of the circuit breaker that rejected the request
 * @property resetTimeout the configured duration used for the half-open probe window
 * @property halfOpenAttempts the current number of half-open attempts already in flight or consumed
 * @property halfOpenRequests the configured maximum number of half-open trial requests
 */
public class CircuitBreakerHalfOpenSaturatedException(
    public val circuitBreakerName: String,
    public val resetTimeout: Duration,
    public val halfOpenAttempts: Int,
    public val halfOpenRequests: Int
) : IllegalStateException(
    "Circuit breaker '$circuitBreakerName' is HALF_OPEN and saturated. " +
        "Requests are rejected until an in-flight probe finishes or the reset timeout ($resetTimeout) elapses. " +
        "Current half-open attempts: $halfOpenAttempts/$halfOpenRequests."
)

private val CircuitBreakerNameKey: AttributeKey<String> = AttributeKey("CircuitBreakerName")

/**
 * Associates this request with the specified named circuit breaker.
 *
 * The circuit breaker should be registered via [CircuitBreakerConfig.register],
 * or it will be created dynamically using the [global][CircuitBreakerConfig.global] defaults.
 *
 * @param name the circuit breaker name to use for this request
 */
public fun HttpRequestBuilder.circuitBreaker(name: String) {
    attributes.put(CircuitBreakerNameKey, name)
}

/**
 * A client plugin implementing the
 * [Circuit Breaker](https://learn.microsoft.com/en-us/azure/architecture/patterns/circuit-breaker)
 * pattern to prevent cascading failures in distributed systems.
 *
 * The circuit breaker monitors consecutive failures for each named service. When the failure count
 * reaches [ServiceCircuitBreakerConfig.failureThreshold], the circuit trips to the Open state and
 * subsequent requests are immediately rejected with [CircuitBreakerOpenException]. After
 * [ServiceCircuitBreakerConfig.resetTimeout] elapses, the circuit enters the Half-Open state,
 * allowing [ServiceCircuitBreakerConfig.halfOpenRequests] trial requests. If all trial requests
 * succeed, the circuit closes and normal operation resumes. If any trial request fails, the circuit
 * re-opens.
 *
 * Usage:
 * ```kotlin
 * val client = HttpClient {
 *     install(CircuitBreaker) {
 *         register("payment-service") {
 *             failureThreshold = 5
 *             resetTimeout = 30.seconds
 *             halfOpenRequests = 3
 *         }
 *     }
 * }
 *
 * client.get("https://payment.example.com/api/charge") {
 *     circuitBreaker("payment-service")
 * }
 * ```
 *
 * @see CircuitBreakerConfig
 * @see CircuitBreakerOpenException
 */
public val CircuitBreaker: ClientPlugin<CircuitBreakerConfig> = createClientPlugin(
    "CircuitBreaker",
    ::CircuitBreakerConfig
) {
    val config = pluginConfig
    val timeSource = config.timeSource

    config.circuits.values.forEach { it.validate() }
    config.globalConfig.validate()

    val instances = mutableMapOf<String, CircuitBreakerInstance>()
    val instancesMutex = Mutex()

    for ((name, serviceConfig) in config.circuits) {
        instances[name] = CircuitBreakerInstance(name, serviceConfig, timeSource)
    }

    suspend fun getInstance(name: String): CircuitBreakerInstance {
        return instancesMutex.withLock {
            instances.getOrPut(name) {
                CircuitBreakerInstance(name, config.globalConfig, timeSource)
            }
        }
    }

    fun resolveCircuitName(request: HttpRequestBuilder): String? {
        request.attributes.getOrNull(CircuitBreakerNameKey)?.let { return it }
        config.requestRouter?.invoke(request)?.let { return it }
        return null
    }

    on(Send) { request ->
        val circuitName = resolveCircuitName(request) ?: return@on proceed(request)
        val instance = getInstance(circuitName)
        val serviceConfig = config.circuits[circuitName] ?: config.globalConfig

        instance.acquirePermission()

        val call = try {
            proceed(request)
        } catch (cause: Throwable) {
            instance.recordFailure()
            throw cause
        }

        if (serviceConfig.failurePredicate(call.response)) {
            instance.recordFailure()
        } else {
            instance.recordSuccess()
        }

        call
    }
}
