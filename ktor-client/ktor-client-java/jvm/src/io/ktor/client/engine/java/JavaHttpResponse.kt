/*
* Copyright 2014-2021 JetBrains s.r.o and contributors. Use of this source code is governed by the Apache 2.0 license.
*/

package io.ktor.client.engine.java

import io.ktor.client.plugins.*
import io.ktor.client.request.*
import kotlinx.coroutines.suspendCancellableCoroutine
import java.net.http.*
import java.util.concurrent.CompletableFuture
import java.util.concurrent.CompletionException
import kotlin.coroutines.*

internal suspend fun HttpClient.executeHttpRequest(
    callContext: CoroutineContext,
    requestData: HttpRequestData
): HttpResponseData? {
    val httpRequest = requestData.convertToHttpRequest(callContext)
    return try {
        sendAsync(httpRequest, JavaHttpResponseBodyHandler(callContext, requestData))?.awaitInterruptible()?.body()
    } catch (cause: HttpConnectTimeoutException) {
        throw ConnectTimeoutException(requestData, cause)
    } catch (cause: HttpTimeoutException) {
        throw HttpRequestTimeoutException(requestData)
    }
}

/**
 * Awaits completion of this future, cancelling it with `mayInterruptIfRunning = true`
 * if the coroutine is cancelled.
 *
 * The JDK HTTP client aborts the HTTP exchange only on `cancel(true)` while the future is incomplete:
 * it closes the HTTP/1.1 connection or resets the HTTP/2 stream.
 * [kotlinx.coroutines.future.await] calls `cancel(false)`, which completes the future
 * but leaves the exchange running.
 */
internal suspend fun <T> CompletableFuture<T>.awaitInterruptible(): T = suspendCancellableCoroutine { continuation ->
    whenComplete { result, cause ->
        if (cause == null) {
            continuation.resume(result)
        } else {
            continuation.resumeWithException((cause as? CompletionException)?.cause ?: cause)
        }
    }
    continuation.invokeOnCancellation { cancel(true) }
}
