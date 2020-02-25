/*
 * Copyright 2014-2020 JetBrains s.r.o and contributors. Use of this source code is governed by the Apache 2.0 license.
 */

package io.ktor.client.engine.ios

import io.ktor.client.request.*
import io.ktor.client.utils.*
import io.ktor.http.*
import io.ktor.util.date.*
import io.ktor.utils.io.*
import kotlinx.coroutines.*
import kotlinx.coroutines.channels.*
import platform.Foundation.*
import platform.darwin.*
import kotlin.coroutines.*

internal class IosResponseReader(private val callContext: CoroutineContext) : NSObject(), NSURLSessionDataDelegateProtocol {
    private val chunks = Channel<ByteArray>(Channel.UNLIMITED)
    private val response = CompletableDeferred<NSURLSessionTask>(callContext[Job])

    private val requestTime = GMTDate()

    suspend fun awaitResponse(): HttpResponseData {
        val task = response.await()
        val rawResponse = task.response as NSHTTPURLResponse

        @Suppress("UNCHECKED_CAST")
        val headersDict = rawResponse.allHeaderFields as Map<String, String>

        val status = HttpStatusCode.fromValue(rawResponse.statusCode.toInt())
        val headers = buildHeaders {
            headersDict.mapKeys { (key, value) -> append(key, value) }
        }

        val responseBody = GlobalScope.writer(Dispatchers.Unconfined, autoFlush = true) {
            chunks.consumeEach {
                channel.writeFully(it)
                channel.flush()
            }
        }.channel

        val version = HttpProtocolVersion.HTTP_1_1

        return HttpResponseData(
            status, requestTime, headers, version,
            responseBody, callContext
        )
    }

    override fun URLSession(session: NSURLSession, dataTask: NSURLSessionDataTask, didReceiveData: NSData) {
        val content = didReceiveData.toByteArray()
        check(chunks.offer(content)) { "Failed to process the received chunk of size: ${content.size}" }
    }

    override fun URLSession(session: NSURLSession, task: NSURLSessionTask, didCompleteWithError: NSError?) {
        chunks.close()

        if (didCompleteWithError != null) {
            response.completeExceptionally(IosHttpRequestException(didCompleteWithError))
            return
        }

        response.complete(task)
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
}
