/*
 * Copyright 2014-2022 JetBrains s.r.o and contributors. Use of this source code is governed by the Apache 2.0 license.
 */

package io.ktor.server.plugins.ratelimit

import io.ktor.ratelimit.LIMITER_NAME_EMPTY
import io.ktor.server.routing.*
import io.ktor.util.*
import io.ktor.utils.io.*

/**
 * Creates a route that uses the unnamed rate limit provider.
 *
 * @param build configures the new route.
 * @return the route with rate limiting applied.
 */
@OptIn(InternalAPI::class)
public fun Route.rateLimit(build: Route.() -> Unit): Route = rateLimit(LIMITER_NAME_EMPTY, build)

/**
 * Creates a route that uses a rate limit provider identified by a shared name.
 *
 * @param configuration the registered provider name.
 * @param build configures the new route.
 * @return the route with rate limiting applied.
 */
@Suppress("DEPRECATION")
public fun Route.rateLimit(configuration: io.ktor.ratelimit.RateLimitName, build: Route.() -> Unit): Route =
    rateLimit(RateLimitName(configuration.name), build)

/**
 * Creates a route with Rate-Limit rules applied to it.
 * This function accepts name of RateLimit providers defined in the [RateLimit] plugin configuration.
 *
 * When combined with other route-scoped guard plugins such as authentication,
 * the relative execution order follows route nesting: interceptors on parent routes run before interceptors
 * on child routes. To use a principal in [RateLimitProviderConfig.requestKey], nest [rateLimit] inside
 * [io.ktor.server.auth.authenticate].
 *
 * [Report a problem](https://ktor.io/feedback/?fqname=io.ktor.server.plugins.ratelimit.rateLimit)
 *
 * @see [RateLimit]
 *
 * @param configuration names of RateLimit providers defined in the [RateLimit] plugin configuration.
 * @throws IllegalArgumentException if there are no registered providers referred by [configuration] names.
 */
@Deprecated("Use the overload accepting io.ktor.ratelimit.RateLimitName.")
@OptIn(InternalAPI::class)
public fun Route.rateLimit(
    configuration: RateLimitName = RateLimitName(LIMITER_NAME_EMPTY.name),
    build: Route.() -> Unit
): Route {
    val rateLimitRoute = createChild(RateLimitRouteSelector(configuration))
    rateLimitRoute.attributes.put(RateLimitProviderNameKey, configuration)
    val allConfigurations = generateSequence(rateLimitRoute) { it.parent }
        .toList()
        .reversed()
        .mapNotNull { it.attributes.getOrNull(RateLimitProviderNameKey) }
        .distinct()

    rateLimitRoute.install(RateLimitInterceptors) {
        this.providerNames = allConfigurations
    }
    rateLimitRoute.build()
    return rateLimitRoute
}

/**
 * A rate-limited route node that is used by the [RateLimit] plugin
 * and usually created by the [Route.rateLimit] DSL function, so generally there is no need to instantiate it directly
 * unless you are writing an extension.
 *
 * [Report a problem](https://ktor.io/feedback/?fqname=io.ktor.server.plugins.ratelimit.RateLimitRouteSelector)
 *
 * @param name of rate-limit provider to be applied to this route.
 */
public class RateLimitRouteSelector(public val name: RateLimitName) : RouteSelector() {
    override suspend fun evaluate(context: RoutingResolveContext, segmentIndex: Int): RouteSelectorEvaluation {
        return RouteSelectorEvaluation.Transparent
    }

    override fun toString(): String = "(RateLimit ${name.name})"
}

private val RateLimitProviderNameKey = AttributeKey<RateLimitName>("RateLimitProviderNameKey")
