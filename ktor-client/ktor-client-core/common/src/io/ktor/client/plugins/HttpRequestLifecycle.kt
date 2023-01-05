/*
* Copyright 2014-2021 JetBrains s.r.o and contributors. Use of this source code is governed by the Apache 2.0 license.
*/

package io.ktor.client.plugins

import io.ktor.client.*
import io.ktor.client.request.*
import io.ktor.util.*
import io.ktor.util.logging.*
import io.ktor.utils.io.*
import kotlinx.coroutines.*

private val LOGGER = KtorSimpleLogger("io.ktor.client.plugins.HttpRequestLifecycle")

/**
 * A client's HTTP plugin that sets up [HttpRequestBuilder.executionContext] and completes it when the pipeline is fully
 * processed.
 */
internal class HttpRequestLifecycle private constructor() {
    /**
     * A companion object for a plugin installation.
     */
    companion object Plugin : HttpClientPlugin<Unit, HttpRequestLifecycle> {

        override val key: AttributeKey<HttpRequestLifecycle> = AttributeKey("RequestLifecycle")

        override fun prepare(block: Unit.() -> Unit): HttpRequestLifecycle = HttpRequestLifecycle()

        override fun install(plugin: HttpRequestLifecycle, scope: HttpClient) {
            scope.requestPipeline.intercept(HttpRequestPipeline.Before) {
                val executionContext = SupervisorJob(context.executionContext)

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
    val handler = clientEngineJob.invokeOnCompletion { cause ->
        if (cause != null) {
            LOGGER.trace("Cancelling request because engine Job failed with error: $cause")
            requestJob.cancel("Engine failed", cause)
        } else {
            LOGGER.trace("Cancelling request because engine Job completed")
            requestJob.complete()
        }
    }

    requestJob.invokeOnCompletion {
        handler.dispose()
    }
}
