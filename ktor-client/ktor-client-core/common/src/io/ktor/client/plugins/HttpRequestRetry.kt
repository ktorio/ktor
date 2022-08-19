/*
 * Copyright 2014-2021 JetBrains s.r.o and contributors. Use of this source code is governed by the Apache 2.0 license.
 */

package io.ktor.client.plugins

import io.ktor.client.*
import io.ktor.client.call.*
import io.ktor.client.plugins.api.*
import io.ktor.client.request.*
import io.ktor.client.statement.*
import io.ktor.events.*
import io.ktor.http.*
import io.ktor.util.*
import kotlinx.coroutines.*
import kotlin.math.*
import kotlin.random.*

@Suppress("NAME_SHADOWING")
internal val HttpRequestRetryPlugin = createClientPlugin("RetryFeature", HttpRequestRetry::Configuration) {

    val shouldRetry: HttpRequestRetry.ShouldRetryContext.(HttpRequest, HttpResponse) -> Boolean =
        pluginConfig.shouldRetry
    val shouldRetryOnException: HttpRequestRetry.ShouldRetryContext.(HttpRequestBuilder, Throwable) -> Boolean =
        pluginConfig.shouldRetryOnException
    val delayMillis: HttpRequestRetry.DelayContext.(Int) -> Long = pluginConfig.delayMillis
    val delay: suspend (Long) -> Unit = pluginConfig.delay
    val maxRetries: Int = pluginConfig.maxRetries
    val modifyRequest: HttpRequestRetry.ModifyRequestContext.(HttpRequestBuilder) -> Unit = pluginConfig.modifyRequest

    fun shouldRetry(
        retryCount: Int,
        maxRetries: Int,
        shouldRetry: HttpRequestRetry.ShouldRetryContext.(HttpRequest, HttpResponse) -> Boolean,
        call: HttpClientCall
    ) = retryCount < maxRetries && shouldRetry(
        HttpRequestRetry.ShouldRetryContext(retryCount + 1),
        call.request,
        call.response
    )

    fun shouldRetryOnException(
        retryCount: Int,
        maxRetries: Int,
        shouldRetry: HttpRequestRetry.ShouldRetryContext.(HttpRequestBuilder, Throwable) -> Boolean,
        subRequest: HttpRequestBuilder,
        cause: Throwable
    ) = retryCount < maxRetries && shouldRetry(HttpRequestRetry.ShouldRetryContext(retryCount + 1), subRequest, cause)

    fun prepareRequest(request: HttpRequestBuilder): HttpRequestBuilder {
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

    on(Send) { request ->
        var retryCount = 0
        val shouldRetry = request.attributes.getOrNull(ShouldRetryPerRequestAttributeKey) ?: shouldRetry
        val shouldRetryOnException =
            request.attributes.getOrNull(ShouldRetryOnExceptionPerRequestAttributeKey) ?: shouldRetryOnException
        val maxRetries = request.attributes.getOrNull(MaxRetriesPerRequestAttributeKey) ?: maxRetries
        val delayMillis = request.attributes.getOrNull(RetryDelayPerRequestAttributeKey) ?: delayMillis
        val modifyRequest = request.attributes.getOrNull(ModifyRequestPerRequestAttributeKey) ?: modifyRequest

        var call: HttpClientCall
        var lastRetryData: HttpRequestRetry.RetryEventData? = null
        while (true) {
            val subRequest = prepareRequest(request)

            val retryData = try {
                if (lastRetryData != null) {
                    val modifyRequestContext = HttpRequestRetry.ModifyRequestContext(
                        request,
                        lastRetryData.response,
                        lastRetryData.cause,
                        lastRetryData.retryCount
                    )
                    modifyRequest(modifyRequestContext, subRequest)
                }
                call = execute(subRequest)
                if (!shouldRetry(retryCount, maxRetries, shouldRetry, call)) {
                    break
                }
                HttpRequestRetry.RetryEventData(subRequest, ++retryCount, call.response, null)
            } catch (cause: Throwable) {
                if (!shouldRetryOnException(retryCount, maxRetries, shouldRetryOnException, subRequest, cause)) {
                    throw cause
                }
                HttpRequestRetry.RetryEventData(subRequest, ++retryCount, null, cause)
            }

            lastRetryData = retryData
            client.monitor.raise(HttpRequestRetry.HttpRequestRetryEvent, lastRetryData)

            val delayContext =
                HttpRequestRetry.DelayContext(lastRetryData.request, lastRetryData.response, lastRetryData.cause)
            delay(delayMillis(delayContext, retryCount))
        }
        call
    }
}

/**
 * A plugin that enables the client to retry failed requests.
 * The default retry policy is 3 retries with exponential delay.
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
 *      retryIf { request, response -> !response.status.isSuccess() }
 *      retryOnExceptionIf { request, cause -> cause is NetworkError }
 *      delayMillis { retry -> retry * 3000 } // will retry in 3, 6, 9, etc. seconds
 *      modifyRequest { it.headers.append("X_RETRY_COUNT", retryCount.toString()) }
 * }
 * ```
 */
public class HttpRequestRetry internal constructor() {

    /**
     * A context for [HttpRequestRetry.Configuration.shouldRetry]
     * and [HttpRequestRetry.Configuration.shouldRetryOnException]
     */
    public class ShouldRetryContext(
        /**
         * A retry count starting from 1
         */
        public val retryCount: Int
    )

    /**
     * A context for [HttpRequestRetry.Configuration.delayMillis].
     * Contains a non-null [response] or [cause] but not both.
     */
    public class DelayContext internal constructor(
        public val request: HttpRequestBuilder,
        public val response: HttpResponse?,
        public val cause: Throwable?
    )

    /**
     * A context for [HttpRequestRetry.Configuration.modifyRequest].
     * Contains a non-null [response] or [cause] but not both.
     */
    public class ModifyRequestContext internal constructor(
        /**
         * An original request
         */
        public val request: HttpRequestBuilder,
        public val response: HttpResponse?,
        public val cause: Throwable?,
        /**
         * A retry count that starts from 1
         */
        public val retryCount: Int,
    )

    /**
     * Data for the [HttpRequestRetryEvent] event. Contains a non-null [response] or [cause] but not both.
     */
    public class RetryEventData internal constructor(
        public val request: HttpRequestBuilder,
        public val retryCount: Int,
        public val response: HttpResponse?,
        public val cause: Throwable?
    )

    /**
     * Contains [HttpRequestRetry] configurations settings.
     */
    @KtorDsl
    public class Configuration {
        internal lateinit var shouldRetry: ShouldRetryContext.(HttpRequest, HttpResponse) -> Boolean
        internal lateinit var shouldRetryOnException: ShouldRetryContext.(HttpRequestBuilder, Throwable) -> Boolean
        internal lateinit var delayMillis: DelayContext.(Int) -> Long
        internal var modifyRequest: ModifyRequestContext.(HttpRequestBuilder) -> Unit = {}
        internal var delay: suspend (Long) -> Unit = { kotlinx.coroutines.delay(it) }

        /**
         * The maximum amount of retries to perform for a request.
         */
        public var maxRetries: Int = 0

        init {
            retryOnExceptionOrServerErrors(3)
            exponentialDelay()
        }

        /**
         * Disables retry.
         */
        public fun noRetry() {
            maxRetries = 0
            shouldRetry = { _, _ -> false }
            shouldRetryOnException = { _, _ -> false }
        }

        /**
         * Modifies a request before retrying.
         */
        public fun modifyRequest(block: ModifyRequestContext.(HttpRequestBuilder) -> Unit) {
            modifyRequest = block
        }

        /**
         * Specifies retry logic for a response. The [block] accepts [HttpRequest] and [HttpResponse]
         * and should return `true` if this request should be retried.
         */
        public fun retryIf(maxRetries: Int = -1, block: ShouldRetryContext.(HttpRequest, HttpResponse) -> Boolean) {
            if (maxRetries != -1) this.maxRetries = maxRetries
            shouldRetry = block
        }

        /**
         * Specifies retry logic for failed requests. The [block] accepts [HttpRequestBuilder]
         * and [Throwable] and should return true if this request should be retried.
         */
        public fun retryOnExceptionIf(
            maxRetries: Int = -1,
            block: ShouldRetryContext.(HttpRequestBuilder, Throwable) -> Boolean
        ) {
            if (maxRetries != -1) this.maxRetries = maxRetries
            shouldRetryOnException = block
        }

        /**
         * Enables retrying a request if an exception is thrown during the [HttpSend] phase
         * and specifies the number of retries.
         */
        public fun retryOnException(maxRetries: Int = -1) {
            retryOnExceptionIf(maxRetries) { _, cause -> cause !is CancellationException }
        }

        /**
         * Enables retrying a request if a 5xx response is received from a server
         * and specifies the number of retries.
         */
        public fun retryOnServerErrors(maxRetries: Int = -1) {
            retryIf(maxRetries) { _, response ->
                response.status.value.let { it in 500..599 }
            }
        }

        /**
         * Enables retrying a request if an exception is thrown during the [HttpSend] phase
         * or a 5xx response is received and specifies the number of retries.
         */
        public fun retryOnExceptionOrServerErrors(maxRetries: Int = -1) {
            retryOnServerErrors(maxRetries)
            retryOnException(maxRetries)
        }

        /**
         * Specifies delay logic for retries. The [block] accepts the number of retries
         * and should return the number of milliseconds to wait before retrying.
         */
        public fun delayMillis(
            respectRetryAfterHeader: Boolean = true,
            block: DelayContext.(retry: Int) -> Long
        ) {
            delayMillis = {
                if (respectRetryAfterHeader) {
                    val retryAfter = response?.headers?.get(HttpHeaders.RetryAfter)?.toLongOrNull()?.times(1000)
                    maxOf(block(it), retryAfter ?: 0)
                } else {
                    block(it)
                }
            }
        }

        /**
         * Specifies a constant delay between retries.
         * This delay equals to `millis + [0..randomizationMs]` milliseconds.
         */
        public fun constantDelay(
            millis: Long = 1000,
            randomizationMs: Long = 1000,
            respectRetryAfterHeader: Boolean = true
        ) {
            check(millis > 0)
            check(randomizationMs >= 0)

            delayMillis(respectRetryAfterHeader) {
                millis + randomMs(randomizationMs)
            }
        }

        /**
         * Specifies an exponential delay between retries, which is calculated using the Exponential backoff algorithm.
         * This delay equals to `base ^ retryCount * 1000 + [0..randomizationMs]`
         */
        public fun exponentialDelay(
            base: Double = 2.0,
            maxDelayMs: Long = 60000,
            randomizationMs: Long = 1000,
            respectRetryAfterHeader: Boolean = true
        ) {
            check(base > 0)
            check(maxDelayMs > 0)
            check(randomizationMs >= 0)

            delayMillis(respectRetryAfterHeader) { retry ->
                val delay = minOf(base.pow(retry).toLong() * 1000L, maxDelayMs)
                delay + randomMs(randomizationMs)
            }
        }

        /**
         * A function that waits for the specified amount of milliseconds. Uses [kotlinx.coroutines.delay] by default.
         * Useful for tests.
         */
        public fun delay(block: suspend (Long) -> Unit) {
            delay = block
        }

        private fun randomMs(randomizationMs: Long): Long =
            if (randomizationMs == 0L) 0L else Random.nextLong(randomizationMs)
    }

    public companion object Plugin : HttpClientPlugin<Configuration, ClientPluginInstance<Configuration>> {
        override val key: AttributeKey<ClientPluginInstance<Configuration>> = AttributeKey("RetryFeature")

        /**
         * Occurs on request retry.
         */
        public val HttpRequestRetryEvent: EventDefinition<RetryEventData> = EventDefinition()

        override fun prepare(block: Configuration.() -> Unit): ClientPluginInstance<Configuration> {
            return HttpRequestRetryPlugin.prepare(block)
        }

        @OptIn(InternalAPI::class)
        override fun install(plugin: ClientPluginInstance<Configuration>, scope: HttpClient) {
            plugin.install(scope)
        }
    }
}

/**
 * Configures the [HttpRequestRetry] plugin on a per-request level.
 */
public fun HttpRequestBuilder.retry(block: HttpRequestRetry.Configuration.() -> Unit) {
    val configuration = HttpRequestRetry.Configuration().apply(block)
    attributes.put(ShouldRetryPerRequestAttributeKey, configuration.shouldRetry)
    attributes.put(ShouldRetryOnExceptionPerRequestAttributeKey, configuration.shouldRetryOnException)
    attributes.put(RetryDelayPerRequestAttributeKey, configuration.delayMillis)
    attributes.put(MaxRetriesPerRequestAttributeKey, configuration.maxRetries)
    attributes.put(ModifyRequestPerRequestAttributeKey, configuration.modifyRequest)
}

private val MaxRetriesPerRequestAttributeKey =
    AttributeKey<Int>("MaxRetriesPerRequestAttributeKey")

private val ShouldRetryPerRequestAttributeKey =
    AttributeKey<HttpRequestRetry.ShouldRetryContext.(HttpRequest, HttpResponse) -> Boolean>(
        "ShouldRetryPerRequestAttributeKey"
    )

private val ShouldRetryOnExceptionPerRequestAttributeKey =
    AttributeKey<HttpRequestRetry.ShouldRetryContext.(HttpRequestBuilder, Throwable) -> Boolean>(
        "ShouldRetryOnExceptionPerRequestAttributeKey"
    )

private val ModifyRequestPerRequestAttributeKey =
    AttributeKey<HttpRequestRetry.ModifyRequestContext.(HttpRequestBuilder) -> Unit>(
        "ModifyRequestPerRequestAttributeKey"
    )

private val RetryDelayPerRequestAttributeKey =
    AttributeKey<HttpRequestRetry.DelayContext.(Int) -> Long>(
        "RetryDelayPerRequestAttributeKey"
    )
