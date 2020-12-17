/*
 * Copyright 2014-2020 JetBrains s.r.o and contributors. Use of this source code is governed by the Apache 2.0 license.
 */

package io.ktor.client.fatures.retry

import io.ktor.client.*
import io.ktor.client.fatures.retry.Retry.*
import io.ktor.client.features.*
import io.ktor.client.request.*
import io.ktor.util.*
import io.ktor.util.network.*
import io.ktor.util.pipeline.*
import io.ktor.utils.io.*
import io.ktor.utils.io.errors.*
import kotlinx.coroutines.*
import kotlin.time.*

/**
 * This feature provides ability to recover from network errors
 * identified as [IOException] and [HttpRequestTimeoutException] and [UnresolvedAddressException].
 * It also may recover from request validation that usually produce [ResponseException]
 * on a non-successful response status code.
 *
 * The number of retries is limited by [retries] property.
 * After every failed attempt it does a fixed time delay of [retryIntervalInSeconds].
 *
 * When all attempts exceeded, [RequestRetriesExceededException] exception is thrown
 * having a list of all errors in the suppressed list and/or a cause.
 *
 * @property retries number of retries to recover
 * @property retryIntervalInSeconds between attempts
 */
public class Retry private constructor(
    public val retries: Int,
    public val retryIntervalInSeconds: Int
) {
    init {
        require(retries > 0) { "Number of attempts should be at least 1" }
        require(retryIntervalInSeconds >= 0) { "Delay between retries shouldn't be negative" }
    }

    /**
     * [Retry] feature configuration. After configuring an instance of the feature,
     * an attempt to change these properties may either fail or make no effect.
     *
     * @property retries is a number of attempts to recover
     * @property retryIntervalInSeconds between attempts
     */
    public class RetryConfig internal constructor() {
        public var retries: Int = 3
        public var retryIntervalInSeconds: Int = 3
    }

    internal suspend fun PipelineContext<Any, HttpRequestBuilder>.processCall(client: HttpClient) {
        val firstAttempt = runCatching {
            proceed()
        }

        val cause = firstAttempt.checkExceptionOrThrowIfNotWhitelisted()

        if (cause == null) {
            context.attributes.remove(AttemptFailures)
        } else {
            handleFailedCall(cause, client)
        }
    }

    private suspend fun PipelineContext<Any, HttpRequestBuilder>.handleFailedCall(
        previousCause: Throwable,
        client: HttpClient
    ) {
        val collectedErrors = addError(previousCause)

        if (collectedErrors.size >= retries) {
            throwErrorOfCollected(retries, collectedErrors)
        }

        @OptIn(ExperimentalTime::class)
        delay(retryIntervalInSeconds.seconds)

        val newContext = HttpRequestBuilder()
        newContext.takeFrom(context)

        val oldJob = context.executionContext as? CompletableJob
        attachToClientEngineJob(newContext.executionContext as CompletableJob, client.coroutineContext[Job]!!)

        val result = runCatching {
            client.requestPipeline.execute(newContext, subject)
        }

        if (result.isSuccess) {
            context.attributes.remove(AttemptFailures)
            oldJob?.cancel()
            finish()
            proceedWith(result.getOrThrow())
        } else {
            result.exceptionOrNull()?.let { cause ->
                oldJob?.completeExceptionally(cause)
                throw cause
            }
        }
    }

    private fun PipelineContext<Any, HttpRequestBuilder>.addError(error: Throwable): List<Throwable> {
        val existing = context.attributes.getOrNull(AttemptFailures) ?: emptyList()
        val resultList = existing + error

        resultList.makeShared()
        context.attributes.put(AttemptFailures, resultList)

        return resultList
    }

    @OptIn(ExperimentalCoroutinesApi::class)
    public class RequestRetriesExceededException(
        override val message: String,
        override val cause: Throwable? = null,
        public val attempts: Int
    ) : Exception(message), CopyableThrowable<RequestRetriesExceededException> {
        override fun createCopy(): RequestRetriesExceededException {
            return RequestRetriesExceededException(message, this, attempts)
        }
    }

    public companion object Feature : HttpClientFeature<RetryConfig, Retry> {
        private val AttemptFailures = AttributeKey<List<Throwable>>("AttemptFailures")

        /**
         * Pipeline phase in which the feature installs.
         */
        public val RetryPhase: PipelinePhase = PipelinePhase("RetryPhase")

        override val key: AttributeKey<Retry> = AttributeKey("Retry")

        override fun prepare(block: RetryConfig.() -> Unit): Retry {
            val config = RetryConfig().apply(block)
            config.makeShared()
            return Retry(config.retries, config.retryIntervalInSeconds).also {
                it.makeShared()
            }
        }

        override fun install(feature: Retry, scope: HttpClient) {
            scope.requestPipeline.insertPhaseBefore(HttpRequestPipeline.Send, RetryPhase)

            if (feature.retries > 1) {
                scope.requestPipeline.intercept(RetryPhase) {
                    with(feature) {
                        processCall(client = scope)
                    }
                }
            }
        }
    }
}

/**
 * Install [Retry] feature with [block] configuration or with default parameters.
 */
public fun HttpClientConfig<*>.retry(block: Retry.RetryConfig.() -> Unit = {}) {
    install(Retry, block)
}
