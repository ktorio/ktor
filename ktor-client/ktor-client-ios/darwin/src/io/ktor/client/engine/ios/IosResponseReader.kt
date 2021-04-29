/*
* Copyright 2014-2021 JetBrains s.r.o and contributors. Use of this source code is governed by the Apache 2.0 license.
*/

package io.ktor.client.engine.ios

import io.ktor.client.request.*
import io.ktor.client.utils.*
import io.ktor.http.*
import io.ktor.util.date.*
import io.ktor.utils.io.*
import io.ktor.utils.io.CancellationException
import kotlinx.coroutines.*
import kotlinx.coroutines.channels.*
import platform.Foundation.*
import platform.darwin.*
import kotlin.coroutines.*

internal class IosResponseReader(
    private val callContext: CoroutineContext,
    private val requestData: HttpRequestData,
    private val config: IosClientEngineConfig
) : NSObject(), NSURLSessionDataDelegateProtocol {
    private val chunks = Channel<ByteArray>(Channel.UNLIMITED)
    private val rawResponse = CompletableDeferred<NSHTTPURLResponse>(callContext[Job])

    private val requestTime = GMTDate()

    public suspend fun awaitResponse(): HttpResponseData {
        val response = rawResponse.await()

        @Suppress("UNCHECKED_CAST")
        val headersDict = response.allHeaderFields as Map<String, String>

        val status = HttpStatusCode.fromValue(response.statusCode.toInt())
        val headers = buildHeaders {
            headersDict.mapKeys { (key, value) -> append(key, value) }
        }

        val responseBody = GlobalScope.writer(callContext + Dispatchers.Unconfined, autoFlush = true) {
            try {
                @OptIn(ExperimentalCoroutinesApi::class)
                chunks.consumeEach {
                    channel.writeFully(it)
                    channel.flush()
                }
            } catch (cause: CancellationException) {
                chunks.cancel(cause)
                throw cause
            }
        }.channel

        val version = HttpProtocolVersion.HTTP_1_1

        return HttpResponseData(
            status,
            requestTime,
            headers,
            version,
            responseBody,
            callContext
        )
    }

    override fun URLSession(session: NSURLSession, dataTask: NSURLSessionDataTask, didReceiveData: NSData) {
        if (!rawResponse.isCompleted) {
            val response = dataTask.response as NSHTTPURLResponse
            rawResponse.complete(response)
        }

        val content = didReceiveData.toByteArray()
        try {
            chunks.offer(content)
        } catch (cause: CancellationException) {
            dataTask.cancel()
        }
    }

    override fun URLSession(session: NSURLSession, task: NSURLSessionTask, didCompleteWithError: NSError?) {
        if (didCompleteWithError != null) {
            val exception = handleNSError(requestData, didCompleteWithError)
            chunks.close(exception)
            rawResponse.completeExceptionally(exception)
            return
        }

        if (!rawResponse.isCompleted) {
            val response = task.response as NSHTTPURLResponse
            rawResponse.complete(response)
        }

        chunks.close()
    }

    override fun URLSession(
        session: NSURLSession,
        task: NSURLSessionTask,
        willPerformHTTPRedirection: NSHTTPURLResponse,
        newRequest: NSURLRequest,
        completionHandler: (NSURLRequest?) -> Unit
    ) {
        completionHandler(null)
    }

    override fun URLSession(
        session: NSURLSession,
        task: NSURLSessionTask,
        didReceiveChallenge: NSURLAuthenticationChallenge,
        completionHandler: (NSURLSessionAuthChallengeDisposition, NSURLCredential?) -> Unit
    ) {
        val handler = config.challengeHandler
        if (handler != null) {
            handler(session, task, didReceiveChallenge, completionHandler)
        } else {
            completionHandler(NSURLSessionAuthChallengePerformDefaultHandling, didReceiveChallenge.proposedCredential)
        }
    }
}
