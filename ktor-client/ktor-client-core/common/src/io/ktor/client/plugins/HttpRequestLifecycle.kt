/*
* Copyright 2014-2021 JetBrains s.r.o and contributors. Use of this source code is governed by the Apache 2.0 license.
*/

package io.ktor.client.plugins

import io.ktor.client.*
import io.ktor.client.plugins.api.*
import io.ktor.client.request.*
import kotlinx.coroutines.*

/**
 * A client's HTTP plugin that sets up [HttpRequestBuilder.executionContext] and completes it when the pipeline is fully
 * processed.
 */
internal val HttpRequestLifecycle = createClientPlugin("RequestLifecycle") {
    on(SetupRequestContext) { request, proceed ->
        val executionContext = Job(request.executionContext)

        attachToClientEngineJob(executionContext, client.coroutineContext[Job]!!)

        try {
            request.executionContext = executionContext
            proceed()
        } catch (cause: Throwable) {
            executionContext.completeExceptionally(cause)
            throw cause
        } finally {
            executionContext.complete()
        }
    }
}

public object SetupRequestContext : ClientHook<suspend (HttpRequestBuilder, suspend () -> Unit) -> Unit> {
    override fun install(client: HttpClient, handler: suspend (HttpRequestBuilder, suspend () -> Unit) -> Unit) {
        client.requestPipeline.intercept(HttpRequestPipeline.Before) {
            handler(context, ::proceed)
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
            requestJob.cancel("Engine failed", cause)
        } else {
            requestJob.complete()
        }
    }

    requestJob.invokeOnCompletion {
        handler.dispose()
    }
}
