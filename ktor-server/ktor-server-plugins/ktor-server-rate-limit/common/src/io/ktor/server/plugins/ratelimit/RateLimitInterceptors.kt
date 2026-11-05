/*
 * Copyright 2014-2026 JetBrains s.r.o and contributors. Use of this source code is governed by the Apache 2.0 license.
 */

package io.ktor.server.plugins.ratelimit

import io.ktor.events.EventDefinition
import io.ktor.http.*
import io.ktor.server.application.*
import io.ktor.server.request.*
import io.ktor.server.response.*
import io.ktor.server.routing.Route
import io.ktor.server.routing.RoutingPipelineCall
import io.ktor.util.collections.*
import io.ktor.util.logging.*
import kotlin.time.Duration

private object ValidatorsPhase : Hook<suspend (ApplicationCall) -> Unit> {
    override fun install(pipeline: ApplicationCallPipeline, handler: suspend (ApplicationCall) -> Unit) {
        pipeline.intercept(ApplicationCallPipeline.Validators) { handler(call) }
    }
}

private object PluginsPhase : Hook<suspend (ApplicationCall) -> Unit> {
    override fun install(pipeline: ApplicationCallPipeline, handler: suspend (ApplicationCall) -> Unit) {
        pipeline.intercept(ApplicationCallPipeline.Plugins) { handler(call) }
    }
}

internal val RateLimitInterceptors = createRouteScopedPlugin(
    "RateLimitInterceptors",
    ::RateLimitInterceptorsConfig,
) {
    rateLimiterPluginBuilder(ValidatorsPhase)
}
internal val RateLimitApplicationInterceptors = createApplicationPlugin(
    "RateLimitApplicationInterceptors",
    ::RateLimitInterceptorsConfig,
) {
    rateLimiterPluginBuilder(PluginsPhase)
}

/**
 * Identifies a [RateLimiter] instance: a rate limiter is shared by all routes
 * that use the same provider and produce the same request key.
 *
 * [Report a problem](https://ktor.io/feedback/?fqname=io.ktor.server.plugins.ratelimit.RateLimiterKey)
 *
 * @property providerName the name of the rate limit provider, or `Global` for the global rate limiter.
 * @property key the request key produced by the provider's `requestKey` function.
 */
public class RateLimiterKey(
    public val providerName: String,
    public val key: Any,
) {
    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other !is RateLimiterKey) return false
        return providerName == other.providerName && key == other.key
    }

    override fun hashCode(): Int = 31 * providerName.hashCode() + key.hashCode()
}

/**
 * A rate limiter's decision for a request.
 *
 * [Report a problem](https://ktor.io/feedback/?fqname=io.ktor.server.plugins.ratelimit.RateLimitEventData)
 *
 * @property limiter identifies the limiter that checked the request.
 * @property route the selected route, or `null` when the request is checked before routing.
 * @property method the HTTP method of the request.
 */
public sealed class RateLimitEventData(
    public val limiter: RateLimiterKey,
    public val route: Route?,
    public val method: HttpMethod,
) {
    /**
     * A request accepted by a rate limiter.
     *
     * [Report a problem](https://ktor.io/feedback/?fqname=io.ktor.server.plugins.ratelimit.RateLimitEventData.Allowed)
     *
     * @param limiter identifies the limiter that checked the request.
     * @param route the selected route, or `null` when checked before routing.
     * @param method the HTTP method of the request.
     * @property tokensLeft the tokens remaining after consumption.
     * @property tokensTotal the limiter's capacity.
     * @property nextRefillTs the epoch time in milliseconds when the bucket will be full without further consumption.
     */
    public class Allowed(
        limiter: RateLimiterKey,
        route: Route?,
        method: HttpMethod,
        public val tokensLeft: Int,
        public val tokensTotal: Int,
        public val nextRefillTs: Long,
    ) : RateLimitEventData(limiter, route, method)

    /**
     * A request rejected by a rate limiter.
     *
     * [Report a problem](https://ktor.io/feedback/?fqname=io.ktor.server.plugins.ratelimit.RateLimitEventData.Rejected)
     *
     * @param limiter identifies the limiter that checked the request.
     * @param route the selected route, or `null` when checked before routing.
     * @param method the HTTP method of the request.
     * @property toWait the duration to wait before retrying the request.
     */
    public class Rejected(
        limiter: RateLimiterKey,
        route: Route?,
        method: HttpMethod,
        public val toWait: Duration,
    ) : RateLimitEventData(limiter, route, method)
}

/**
 * Raised after a rate limiter checks a request.
 *
 * [Report a problem](https://ktor.io/feedback/?fqname=io.ktor.server.plugins.ratelimit.RateLimitEvent)
 */
public val RateLimitEvent: EventDefinition<RateLimitEventData> = EventDefinition()

private fun PluginBuilder<RateLimitInterceptorsConfig>.rateLimiterPluginBuilder(
    hook: Hook<suspend (ApplicationCall) -> Unit>,
) {
    val configs = application.attributes.getOrNull(RateLimiterConfigsRegistryKey) ?: emptyMap()
    val providers = pluginConfig.providerNames.map { name ->
        configs[name] ?: throw IllegalStateException(
            "Rate limit provider with name $name is not configured. " +
                "Make sure that you install RateLimit plugin before you use it in Routing"
        )
    }
    val registry = application.attributes.computeIfAbsent(RateLimiterInstancesRegistryKey) { ConcurrentMap() }
    val monitor = application.monitor

    on(hook) { call ->
        providers.forEach { provider ->
            if (call.isHandled) return@on

            LOGGER.trace { "Using rate limit ${provider.name} for ${call.request.uri}" }
            val key = provider.requestKey(call)
            val weight = provider.requestWeight(call, key)
            LOGGER.trace { "Using key=$key and weight=$weight for ${call.request.uri}" }

            val providerKey = ProviderKey(provider.name, key)
            val rateLimiterForCall = registry.computeIfAbsent(providerKey) {
                val factory = provider.rateLimiterFactory

                if (factory != null) {
                    factory(key)
                } else {
                    checkNotNull(provider.rateLimiter)(call, key)
                }
            }
            call.attributes.put(
                RateLimitersForCallKey,
                call.attributes.getOrNull(RateLimitersForCallKey).orEmpty() + Pair(provider.name, rateLimiterForCall)
            )

            val state = rateLimiterForCall.tryConsume(weight)
            provider.modifyResponse(call, state)

            val limiterKey = RateLimiterKey(
                providerName = if (provider.name == LIMITER_NAME_GLOBAL) "Global" else provider.name.name,
                key = key,
            )
            val route = if (call is RoutingPipelineCall) call.route else null
            val event = when(state) {
                is RateLimiter.State.Available -> {
                    RateLimitEventData.Allowed(
                        limiter = limiterKey,
                        route = route,
                        method = call.request.httpMethod,
                        tokensTotal = state.limit,
                        tokensLeft = state.remainingTokens,
                        nextRefillTs = state.refillAtTimeMillis
                    )
                }
                is RateLimiter.State.Exhausted ->  {
                    RateLimitEventData.Rejected(
                        limiter = limiterKey,
                        route = route,
                        method = call.request.httpMethod,
                        toWait = state.toWait
                    )
                }
            }

            monitor.raise(RateLimitEvent, event)
            when (state) {
                is RateLimiter.State.Exhausted -> {
                    LOGGER.trace { "Declining ${call.request.uri} because of too many requests" }
                    call.respond(HttpStatusCode.TooManyRequests)
                }

                is RateLimiter.State.Available -> {
                    LOGGER.trace { "Allowing ${call.request.uri}" }
                }
            }
        }
    }
}

internal class RateLimitInterceptorsConfig {
    internal var providerNames: List<RateLimitName> = listOf(LIMITER_NAME_EMPTY)
}
