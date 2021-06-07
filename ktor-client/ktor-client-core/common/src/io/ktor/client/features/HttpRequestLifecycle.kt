/*
* Copyright 2014-2021 JetBrains s.r.o and contributors. Use of this source code is governed by the Apache 2.0 license.
*/

package io.ktor.client.features

import io.ktor.client.*
import io.ktor.client.request.*
import io.ktor.util.*
import io.ktor.utils.io.*
import kotlinx.coroutines.*

/**
 * Client HTTP feature that sets up [HttpRequestBuilder.executionContext] and completes it when the pipeline is fully
 * processed.
 */
internal class HttpRequestLifecycle {
    /**
     * Companion object for feature installation.
     */
    companion object Feature : HttpClientFeature<Unit, HttpRequestLifecycle> {

        override val key: AttributeKey<HttpRequestLifecycle> = AttributeKey("RequestLifecycle")

        override fun prepare(block: Unit.() -> Unit): HttpRequestLifecycle = HttpRequestLifecycle()

        override fun install(feature: HttpRequestLifecycle, scope: HttpClient) {
            scope.requestPipeline.intercept(HttpRequestPipeline.Before) {
                val executionContext = Job(context.executionContext).also {
                    it.makeShared()
                }

                attachToClientEngineJob(executionContext, scope.coroutineContext[Job]!!)

                try {
                    context.executionContext = executionContext
                    proceed()
                } catch (cause: Throwable) {
                    executionContext.completeExceptionally(cause)
                    throw cause
                } finally {
                    executionContext.complete()
                }
            }
        }
    }
}

/**
 * Attach client engine job.
 */
private fun attachToClientEngineJob(
    requestJob: CompletableJob,
    clientEngineJob: Job
) {
    clientEngineJob.makeShared()

    val handler = clientEngineJob.invokeOnCompletion { cause ->
        if (cause != null) {
            requestJob.cancel("Engine failed", cause)
        } else {
            requestJob.complete()
        }
    }

    requestJob.invokeOnCompletion {
        handler.dispose()
    }
}
