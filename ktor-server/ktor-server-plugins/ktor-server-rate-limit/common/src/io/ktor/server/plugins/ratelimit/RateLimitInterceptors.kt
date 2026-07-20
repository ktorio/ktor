/*
 * Copyright 2014-2026 JetBrains s.r.o and contributors. Use of this source code is governed by the Apache 2.0 license.
 */

package io.ktor.server.plugins.ratelimit

import io.ktor.http.*
import io.ktor.server.application.*
import io.ktor.server.request.*
import io.ktor.server.response.*
import io.ktor.util.collections.*
import io.ktor.util.date.*
import kotlinx.coroutines.*
import kotlin.time.Duration.Companion.milliseconds

private object ValidatorsPhase : Hook<suspend (ApplicationCall) -> Unit> {
    override fun install(pipeline: ApplicationCallPipeline, handler: suspend (ApplicationCall) -> Unit) {
        @Suppress("INVISIBLE_REFERENCE")
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
    val clearOnRefillJobs = ConcurrentMap<ProviderKey, Job>()

    on(hook) { call ->
        providers.forEach { provider ->
            if (call.isHandled) return@on

            LOGGER.trace("Using rate limit ${provider.name} for ${call.request.uri}")
            val key = provider.requestKey(call)
            val weight = provider.requestWeight(call, key)
            LOGGER.trace("Using key=$key and weight=$weight for ${call.request.uri}")

            val providerKey = ProviderKey(provider.name, key)
            val rateLimiterForCall = registry.computeIfAbsent(providerKey) {
                provider.rateLimiter(call, key)
            }
            call.attributes.put(
                RateLimitersForCallKey,
                call.attributes.getOrNull(RateLimitersForCallKey).orEmpty() + Pair(provider.name, rateLimiterForCall)
            )

            val state = rateLimiterForCall.tryConsume(weight)
            provider.modifyResponse(call, state)
            when (state) {
                is RateLimiter.State.Exhausted -> {
                    LOGGER.trace("Declining ${call.request.uri} because of too many requests")
                    call.respond(HttpStatusCode.TooManyRequests)
                }

                is RateLimiter.State.Available -> {
                    if (rateLimiterForCall != RateLimiter.Unlimited) {
                        clearOnRefillJobs[providerKey]?.cancel()
                        clearOnRefillJobs[providerKey] = application.launch {
                            val duration = state.refillAtTimeMillis - getTimeMillis()
                            delay(duration.milliseconds)
                            registry.remove(providerKey, rateLimiterForCall)
                            clearOnRefillJobs.remove(providerKey)
                        }
                    }
                    LOGGER.trace("Allowing ${call.request.uri}")
                }
            }
        }
    }
}

internal class RateLimitInterceptorsConfig {
    internal var providerNames: List<RateLimitName> = listOf(LIMITER_NAME_EMPTY)
}
