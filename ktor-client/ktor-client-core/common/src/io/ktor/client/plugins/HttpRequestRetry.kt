/*
 * Copyright 2014-2021 JetBrains s.r.o and contributors. Use of this source code is governed by the Apache 2.0 license.
 */

package io.ktor.client.plugins

import io.ktor.client.*
import io.ktor.client.call.*
import io.ktor.client.request.*
import io.ktor.client.statement.*
import io.ktor.events.*
import io.ktor.util.*
import kotlinx.coroutines.*
import kotlin.coroutines.*
import kotlin.math.*
import kotlin.native.concurrent.*
import kotlin.random.*

/**
 * Plugin that provides retry functionality. Default retry policy is no retry.
 * Typical usages:
 * ```
 * // use predefined retry policies
 * install(HttpRequestRetry) {
 *      retryOnServerErrors(maxRetries = 3)
 *      exponentialDelay()
 * }
 *
 * // use custom policies
 * install(HttpRequestRetry) {
 *      maxRetries = 5
 *      retryIf { request, response -> !response.status().isSuccess() }
 *      retryOnExceptionIf { request, cause -> cause is NetworkError }
 *      delayMillis { retry -> retry * 3000 } // will retry in 3, 6, 9, etc. seconds
 * }
 * ```
 */
public class HttpRequestRetry internal constructor(configuration: Configuration) {

    /**
     * Data for [HttpRequestRetryEvent] event. Contains non-null [response] or [cause], but not both
     */
    public class RetryEventData internal constructor(
        public val request: HttpRequestBuilder,
        public val retryCount: Int,
        public val response: HttpResponse?,
        public val cause: Throwable?
    )

    private val shouldRetry: (HttpRequest, HttpResponse) -> Boolean = configuration.shouldRetry
    private val shouldRetryOnException: (HttpRequestBuilder, Throwable) -> Boolean =
        configuration.shouldRetryOnException
    private val delayMillis: (Int) -> Long = configuration.delayMillis
    private val delay: suspend (Long) -> Unit = configuration.delay
    private val maxRetries: Int = configuration.maxRetries

    /**
     * Configuration object. Use [retryIf], [retryOnExceptionIf] and [delay] to provide custom
     * retry and delay policies or use one of the predefined.
     */
    public class Configuration {
        internal lateinit var shouldRetry: (HttpRequest, HttpResponse) -> Boolean
        internal lateinit var shouldRetryOnException: (HttpRequestBuilder, Throwable) -> Boolean
        internal lateinit var delayMillis: (Int) -> Long
        internal var delay: suspend (Long) -> Unit = { kotlinx.coroutines.delay(it) }

        /**
         * Maximum retries count
         */
        public var maxRetries: Int = 0

        init {
            noRetry()
            exponentialDelay()
        }

        /**
         * Disables retry
         */
        public fun noRetry() {
            maxRetries = 0
            shouldRetry = { _, _ -> false }
            shouldRetryOnException = { _, _ -> false }
        }

        /**
         * Custom retry logic for response. The [block] accepts [HttpRequest] and [HttpResponse]
         * and should return `true` if this plugin need to retry this request of `false` otherwise
         */
        public fun retryIf(maxRetries: Int = -1, block: (HttpRequest, HttpResponse) -> Boolean) {
            if (maxRetries != -1) this.maxRetries = maxRetries
            shouldRetry = block
        }

        /**
         * Custom retry logic for failed requests. The [block] accepts [HttpRequestBuilder] and an [Throwable]
         * and should return `true` if this plugin need to retry this request of `false` otherwise
         */
        public fun retryOnExceptionIf(maxRetries: Int = -1, block: (HttpRequestBuilder, Throwable) -> Boolean) {
            if (maxRetries != -1) this.maxRetries = maxRetries
            shouldRetryOnException = block
        }

        /**
         * Will retry if exception was thrown during [HttpSend] phase.
         * This method will not retry if the exception is [CancellationException]
         */
        public fun retryOnException(maxRetries: Int = -1) {
            retryOnExceptionIf(maxRetries) { _, cause -> cause !is CancellationException }
        }

        /**
         * Will retry if exceptions was thrown during [HttpSend] phase
         * or 5XX responses was received
         */
        public fun retryOnServerErrors(maxRetries: Int = -1) {
            retryIf(maxRetries) { _, response ->
                response.status.value.let { it in 500..599 }
            }
        }

        /**
         * Will retry if exceptions was thrown during [HttpSend] phase
         * or 5XX responses was received
         */
        public fun retryOnExceptionOrServerErrors(maxRetries: Int = -1) {
            retryOnServerErrors(maxRetries)
            retryOnException(maxRetries)
        }

        /**
         * Custom delay logic. The [block] accepts retry number
         * and should return amount of milliseconds to wait before retrying
         */
        public fun delayMillis(block: (retry: Int) -> Long) {
            delayMillis = block
        }

        /**
         * Constant delay of `millis + [0..randomizationMs]` milliseconds
         */
        public fun constantDelay(millis: Long = 1000, randomizationMs: Long = 1000) {
            check(millis > 0)
            check(randomizationMs >= 0)

            delayMillis {
                millis + randomMs(randomizationMs)
            }
        }

        /**
         * Delay policy that implements [Exponential Backoff](https://en.wikipedia.org/wiki/Exponential_backoff)
         * pattern. Retries will be calculated using `base ^ retryCount + [0..randomizationMs]`
         */
        public fun exponentialDelay(
            base: Double = 2.0,
            maxDelayMs: Long = 60000,
            randomizationMs: Long = 1000,
        ) {
            check(base > 0)
            check(maxDelayMs > 0)
            check(randomizationMs >= 0)

            delayMillis { retry ->
                val delay = minOf(base.pow(retry).toLong() * 1000L, maxDelayMs)
                delay + randomMs(randomizationMs)
            }
        }

        /**
         * Function that awaits for specified amount of milliseconds. Uses [kotlinx.coroutines.delay] by default.
         * Useful for tests.
         */
        public fun delay(block: suspend (Long) -> Unit) {
            delay = block
        }

        private fun randomMs(randomizationMs: Long): Long =
            if (randomizationMs == 0L) 0L else Random.nextLong(randomizationMs)
    }

    internal fun intercept(client: HttpClient) {
        client[HttpSend].intercept { request ->
            var retryCount = 0
            val shouldRetry = request.attributes.getOrNull(ShouldRetryPerRequestAttributeKey) ?: shouldRetry
            val shouldRetryOnException =
                request.attributes.getOrNull(ShouldRetryOnExceptionPerRequestAttributeKey) ?: shouldRetryOnException
            val maxRetries = request.attributes.getOrNull(MaxRetriesPerRequestAttributeKey) ?: maxRetries
            val delayMillis = request.attributes.getOrNull(RetryDelayPerRequestAttributeKey) ?: delayMillis

            var call: HttpClientCall
            while (true) {
                val subRequest = prepareRequest(request)

                val retryData = try {
                    call = execute(subRequest)
                    if (!shouldRetry(retryCount, maxRetries, shouldRetry, call)) {
                        break
                    }
                    RetryEventData(subRequest, ++retryCount, call.response, null)
                } catch (cause: Throwable) {
                    if (!shouldRetryOnException(retryCount, maxRetries, shouldRetryOnException, subRequest, cause)) {
                        throw cause
                    }
                    RetryEventData(subRequest, ++retryCount, null, cause)
                }

                client.monitor.raise(HttpRequestRetryEvent, retryData)

                delay(delayMillis(retryCount))
            }
            call
        }
    }

    private fun shouldRetry(
        retryCount: Int,
        maxRetries: Int,
        shouldRetry: (HttpRequest, HttpResponse) -> Boolean,
        call: HttpClientCall
    ) = retryCount < maxRetries && shouldRetry(call.request, call.response)

    private fun shouldRetryOnException(
        retryCount: Int,
        maxRetries: Int,
        shouldRetry: (HttpRequestBuilder, Throwable) -> Boolean,
        subRequest: HttpRequestBuilder,
        cause: Throwable
    ) = retryCount < maxRetries && shouldRetry(subRequest, cause)

    private fun prepareRequest(request: HttpRequestBuilder): HttpRequestBuilder {
        val subRequest = HttpRequestBuilder().takeFrom(request)
        val subRequestJob = Job()
        subRequest.executionContext = subRequestJob
        CoroutineScope(request.executionContext).launch {
            subRequestJob.join()
        }
        request.executionContext.invokeOnCompletion {
            when (it) {
                null -> subRequestJob.complete()
                else -> subRequestJob.completeExceptionally(it)
            }
        }
        return subRequest
    }

    public companion object Plugin : HttpClientPlugin<Configuration, HttpRequestRetry> {
        override val key: AttributeKey<HttpRequestRetry> = AttributeKey("RetryFeature")

        /**
         * Occurs on request retry.
         */
        public val HttpRequestRetryEvent: EventDefinition<RetryEventData> = EventDefinition()

        override fun prepare(block: Configuration.() -> Unit): HttpRequestRetry {
            val configuration = Configuration().apply(block)
            return HttpRequestRetry(configuration)
        }

        override fun install(plugin: HttpRequestRetry, scope: HttpClient) {
            plugin.intercept(scope)
        }
    }
}

/**
 * Configures [HttpRequestRetry] plugin on per request level
 */
public fun HttpRequestBuilder.retry(block: HttpRequestRetry.Configuration.() -> Unit) {
    val configuration = HttpRequestRetry.Configuration().apply(block)
    attributes.put(ShouldRetryPerRequestAttributeKey, configuration.shouldRetry)
    attributes.put(ShouldRetryOnExceptionPerRequestAttributeKey, configuration.shouldRetryOnException)
    attributes.put(RetryDelayPerRequestAttributeKey, configuration.delayMillis)
    attributes.put(MaxRetriesPerRequestAttributeKey, configuration.maxRetries)
}

@SharedImmutable
private val MaxRetriesPerRequestAttributeKey =
    AttributeKey<Int>("MaxRetriesPerRequestAttributeKey")

@SharedImmutable
private val ShouldRetryPerRequestAttributeKey =
    AttributeKey<(HttpRequest, HttpResponse) -> Boolean>("ShouldRetryPerRequestAttributeKey")

@SharedImmutable
private val ShouldRetryOnExceptionPerRequestAttributeKey =
    AttributeKey<(HttpRequestBuilder, Throwable) -> Boolean>("ShouldRetryOnExceptionPerRequestAttributeKey")

@SharedImmutable
private val RetryDelayPerRequestAttributeKey = AttributeKey<(Int) -> Long>("RetryDelayPerRequestAttributeKey")
