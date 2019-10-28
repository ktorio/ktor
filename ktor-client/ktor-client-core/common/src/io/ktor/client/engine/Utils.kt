/*
 * Copyright 2014-2019 JetBrains s.r.o and contributors. Use of this source code is governed by the Apache 2.0 license.
 */

package io.ktor.client.engine

import io.ktor.client.utils.*
import io.ktor.http.*
import io.ktor.http.content.*
import io.ktor.util.*
import kotlinx.coroutines.*
import kotlin.coroutines.*

/**
 * Default user agent to use in ktor client.
 */
@InternalAPI
val KTOR_DEFAULT_USER_AGENT = "Ktor client"

/**
 * Merge headers from [content] and [requestHeaders] according to [OutgoingContent] properties
 */
@InternalAPI
fun mergeHeaders(
    requestHeaders: Headers,
    content: OutgoingContent,
    block: (key: String, value: String) -> Unit
) {
    buildHeaders {
        appendAll(requestHeaders)
        appendAll(content.headers)
    }.forEach { key, values ->
        if (HttpHeaders.ContentLength == key) return@forEach // set later
        if (HttpHeaders.ContentType == key) return@forEach // set later

        block(key, values.joinToString(";"))
    }

    if (requestHeaders[HttpHeaders.UserAgent] == null && content.headers[HttpHeaders.UserAgent] == null) {
        block(HttpHeaders.UserAgent, KTOR_DEFAULT_USER_AGENT)
    }

    val type = content.contentType?.toString() ?: content.headers[HttpHeaders.ContentType]
    val length = content.contentLength?.toString() ?: content.headers[HttpHeaders.ContentLength]

    type?.let { block(HttpHeaders.ContentType, it) }
    length?.let { block(HttpHeaders.ContentLength, it) }
}

/**
 * Returns current call context if exists, otherwise null.
 */
@InternalAPI
suspend fun callContext(): CoroutineContext? = coroutineContext[KtorCallContextElement]?.let {
    coroutineContext + it.callJob
}

/**
 * Coroutine context element containing call job.
 */
internal class KtorCallContextElement(val callJob: CompletableJob) : CoroutineContext.Element {
    override val key: CoroutineContext.Key<*>
        get() = KtorCallContextElement

    companion object : CoroutineContext.Key<KtorCallContextElement>
}

/**
 * Attach [callJob] to user job using the following logic: when user job completes with exception, [callJob] completes
 * with exception too.
 */
@UseExperimental(InternalCoroutinesApi::class)
internal suspend inline fun attachToUserJob(callJob: Job) {
    val userJob = coroutineContext[Job]!!

    val cleanupHandler = userJob.invokeOnCompletion(onCancelling = true) { cause ->
        cause ?: return@invokeOnCompletion
        callJob.cancel(CancellationException(cause.message))
    }

    callJob.invokeOnCompletion {
        cleanupHandler.dispose()
    }
}
