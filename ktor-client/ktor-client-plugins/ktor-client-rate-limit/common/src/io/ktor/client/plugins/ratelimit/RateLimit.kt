/*
 * Copyright 2014-2026 JetBrains s.r.o and contributors. Use of this source code is governed by the Apache 2.0 license.
 */

package io.ktor.client.plugins.ratelimit

import io.ktor.client.plugins.api.*
import io.ktor.http.*
import io.ktor.ratelimit.*
import io.ktor.util.collections.*
import io.ktor.utils.io.*
import kotlinx.atomicfu.atomic
import kotlinx.coroutines.cancel
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.job

/**
 * Limits outgoing request attempts, suspending requests in FIFO order until tokens are available.
 *
 * The global provider applies automatically; nameless and named providers require explicit request selection.
 * Each provider and key has an unbounded queue. Each provider allows up to five seconds of waiting by default.
 * A provider with a non-positive maximum wait time rejects immediately when tokens are unavailable.
 * Waiting does not occupy an engine connection, and cancelling a request removes it from the queue.
 * Closing the client cancels waiting requests. Completing an HTTP request does not return consumed tokens.
 * Server cooldowns are checked first, then global and selected providers acquire tokens in selection order.
 * Consumed tokens are not returned if admission to a later provider fails.
 * Server Retry-After and exhausted quota headers postpone future admission for the response's origin,
 * independently of providers. Waiting for the server consumes no provider tokens and continues until the cooldown
 * expires or the request is cancelled. Provider wait budgets start after the server cooldown ends.
 * Supports Retry-After seconds and HTTP dates, draft RateLimit items with r/t parameters, legacy RateLimit-Remaining
 * with relative RateLimit-Reset, and X-RateLimit/X-Rate-Limit remaining/reset headers with Unix epoch resets.
 * X-RateLimit-Reset-After is interpreted as relative seconds, including fractional seconds.
 * Server hints do not replace local quotas or automatically retry responses.
 */
@OptIn(InternalAPI::class)
public val RateLimit: ClientPlugin<RateLimitConfig> = createClientPlugin("RateLimit", ::RateLimitConfig) {
    val global = pluginConfig.global
    val providers = pluginConfig.providers.toMap()
    check(global != null || providers.isNotEmpty()) { "At least one provider must be specified" }
    val queues = RateLimiterQueues()
    val cooldown = RateLimitCooldown()
    val serverCooldownExtractor = pluginConfig.serverCooldownExtractor

    on(SendingRequest) { request, _ ->
        val selectedNames = request.attributes.getOrNull(RateLimitProviderNamesKey).orEmpty()
        val selected = selectedNames.map { name ->
            requireNotNull(providers[name]) { "Rate limit provider '${name.name}' is not registered" }
        }
        val applicable = listOfNotNull(global) + selected
        val maxWaitTimeOverrides = request.attributes.getOrNull(RateLimitMaxWaitTimesKey).orEmpty()
        val origin = RateLimitOrigin.from(request.url.build())

        coroutineScope {
            val admission = coroutineContext.job
            val cancellation = request.executionContext.invokeOnCompletion { cause ->
                if (cause != null) {
                    admission.cancel("HTTP request was cancelled while waiting for rate limit tokens", cause)
                }
            }
            try {
                cooldown.await(origin)
                for (provider in applicable) {
                    val key = provider.requestKey(request)
                    val weight = provider.requestWeight(request, key)
                    require(weight > 0) { "Request weight must be positive, but was $weight" }
                    val queue = queues.get(provider, key)
                    try {
                        queue.acquire(
                            weight,
                            maxWaitTime = maxWaitTimeOverrides[provider.name] ?: provider.maxWaitTime
                        )
                    } catch (cause: RateLimitRejectedException) {
                        val name = when (provider.name) {
                            LIMITER_NAME_GLOBAL -> "global"
                            LIMITER_NAME_EMPTY -> "default"
                            else -> provider.name.name
                        }
                        throw RateLimitRejectedException(
                            cause.maxWaitTime,
                            "Provider $name rejected the ${request.method.value} ${request.url.encodedPath} request: " +
                                cause.message
                        )
                    }
                }
            } finally {
                cancellation.dispose()
            }
        }
    }

    onResponse { response ->
        val delay = serverCooldownExtractor(response) ?: return@onResponse
        val url = response.call.request.url
        val origin = RateLimitOrigin.from(url)
        cooldown.postpone(origin, delay)
    }

    onClose {
        cooldown.close()
        queues.close()
    }
}

private data class ProviderKey(val name: RateLimitName, val key: Any)

private data class RateLimitOrigin(val scheme: String, val host: String, val port: Int) {
    companion object {
        fun from(url: Url): RateLimitOrigin =
            RateLimitOrigin(url.protocol.name.lowercase(), url.host.lowercase(), url.port)
    }
}

@OptIn(InternalAPI::class)
private class RateLimiterQueues {
    private val queues = ConcurrentMap<ProviderKey, RateLimiterQueue>()
    private val closed = atomic(false)

    fun get(provider: RateLimitProvider, key: Any): RateLimiterQueue {
        val queue = queues.computeIfAbsent(ProviderKey(provider.name, key)) {
            RateLimiterQueue(provider.rateLimiterFactory(key), provider.maxWaitTime)
        }
        if (closed.value) queue.close()
        return queue
    }

    fun close() {
        closed.value = true
        queues.values.forEach { it.close() }
    }
}
