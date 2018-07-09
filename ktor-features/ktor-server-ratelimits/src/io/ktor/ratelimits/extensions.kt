@file:Suppress("Unused")
package io.ktor.ratelimits

import io.ktor.application.ApplicationCall
import io.ktor.application.ApplicationCallPipeline
import io.ktor.application.feature
import io.ktor.http.HttpStatusCode
import io.ktor.pipeline.ContextDsl
import io.ktor.routing.Route
import io.ktor.routing.application
import io.ktor.routing.route
import java.time.Duration
import java.util.concurrent.TimeUnit

// Pipeline Phases

/**
 * Phase for handling a [RateLimit] for the call.
 */
val ApplicationCallPipeline.ApplicationPhase.RateLimit get() = RateLimits.ApplicationCallPhase

// Routing Extensions

/**
 * Creates a [Route] on the specific [path] with a maximum
 * [limit] of requests, resetting after a certain number of
 * [seconds].
 *
 * This is a shortcut method to simplify this:
 * ```kotlin
 * route(path) {
 *     rateLimit(limit, seconds) {
 *         // block code
 *     }
 * }
 * ```
 *
 * @receiver The parent [Route].
 * @param path The path of the [Route] to create.
 * @param limit The limit on uses.
 * @param seconds The number of seconds for this rate limit to reset.
 * @param block The function to build routes under the rate-limit with.
 *
 * @return The [Route] created.
 */
@ContextDsl fun Route.rateLimit(path: String, limit: Int, seconds: Int, block: Route.() -> Unit): Route {
    return rateLimit(path, limit, Duration.ofSeconds(seconds.toLong()), block)
}

/**
 * Creates a [Route] on the specific [path] with a maximum
 * [limit] of requests, resetting after a certain [length]
 * of [unit].
 *
 * This is a shortcut method to simplify this:
 * ```kotlin
 * route(path) {
 *     rateLimit(limit, length, unit) {
 *         // block code
 *     }
 * }
 * ```
 *
 * @receiver The parent [Route].
 * @param path The path of the [Route] to create.
 * @param limit The limit on uses.
 * @param length The length of time for this rate limit to reset.
 * @param unit The [TimeUnit] the [length] is measured with.
 * @param block The function to build routes under the rate-limit with.
 *
 * @return The [Route] created.
 */
@ContextDsl fun Route.rateLimit(path: String, limit: Int, length: Long, unit: TimeUnit, block: Route.() -> Unit): Route {
    return rateLimit(path, limit, Duration.of(length, chronoUnitOf(unit)), block)
}

/**
 * Creates a [Route] on the specific [path] with a maximum
 * [limit] of requests, resetting after a certain [reset]
 * [Duration].
 *
 * This is a shortcut method to simplify this:
 * ```kotlin
 * route(path) {
 *     rateLimit(limit, reset) {
 *         // block code
 *     }
 * }
 * ```
 *
 * @receiver The parent [Route].
 * @param path The path of the [Route] to create.
 * @param limit The limit on uses.
 * @param reset The [Duration] for this rate limit to reset after.
 * @param block The function to build routes under the rate-limit with.
 *
 * @return The [Route] created.
 */
@ContextDsl fun Route.rateLimit(path: String, limit: Int, reset: Duration, block: Route.() -> Unit): Route {
    return route(path) { rateLimit(limit, reset, block) }
}

/**
 * Creates a [rate limited][RateLimits] block where all child routes of the [block]
 * function are rate-limited.
 *
 * @receiver The parent [Route].
 * @param limit The maximum number of uses before child routes respond with [429][HttpStatusCode].
 * @param seconds The number of seconds that must elapse for child routes to reset.
 * @param block The function to build routes under the rate-limit with.
 *
 * @return The resulting [Route].
 */
@ContextDsl fun Route.rateLimit(limit: Int, seconds: Int, block: Route.() -> Unit): Route {
    return rateLimit(limit, Duration.ofSeconds(seconds.toLong()), block)
}

/**
 * Creates a [rate limited][RateLimits] block where all child routes of the [block]
 * function are rate-limited.
 *
 * @receiver The parent [Route].
 * @param limit The maximum number of uses before child routes respond with [429][HttpStatusCode].
 * @param length The time that must elapse for child routes to reset.
 * @param unit The unit of which the [length] is measured in.
 * @param block The function to build routes under the rate-limit with.
 *
 * @return The resulting [Route].
 */
@ContextDsl fun Route.rateLimit(limit: Int, length: Long, unit: TimeUnit, block: Route.() -> Unit): Route {
    return rateLimit(limit, Duration.of(length, chronoUnitOf(unit)), block)
}

/**
 * Creates a [rate limited][RateLimits] block where all child routes of the [block]
 * function are rate-limited.
 *
 * @receiver The parent [Route].
 * @param limit The maximum number of uses before child routes respond with [429][HttpStatusCode].
 * @param reset The duration that must elapse for child routes to reset.
 * @param block The function to build routes under the rate-limit with.
 *
 * @return The resulting [Route].
 */
@ContextDsl fun Route.rateLimit(limit: Int, reset: Duration, block: Route.() -> Unit): Route {
    val rateLimits = application.feature(RateLimits)
    val route = rateLimits.interceptPipeline(this, limit, reset)
    route.block()
    return route
}

// Call Extensions

/**
 * Gets the [RateLimit] for this [ApplicationCall].
 *
 * If the [ApplicationCall] is part of a route that isn't rate limited,
 * this will throw an [IllegalStateException].
 *
 * @throws IllegalStateException If the [ApplicationCall] is part of a route that is not rate limited.
 */
val ApplicationCall.rateLimit: RateLimit get() {
    application.feature(RateLimits) // Call this to check if the feature is installed
    return checkNotNull(attributes.getOrNull(RateLimits.rateLimitKey)) { "Route is not rate limited!" }
}
