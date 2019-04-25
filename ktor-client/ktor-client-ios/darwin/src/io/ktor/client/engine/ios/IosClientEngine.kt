package io.ktor.client.engine.ios

import io.ktor.client.call.*
import io.ktor.client.engine.*
import io.ktor.client.request.*
import io.ktor.client.utils.*
import io.ktor.http.*
import io.ktor.http.content.*
import io.ktor.util.date.*
import kotlinx.coroutines.*
import kotlinx.coroutines.channels.*
import kotlinx.coroutines.io.*
import kotlinx.io.core.*
import platform.Foundation.*
import platform.darwin.*
import kotlin.coroutines.*

internal class IosClientEngine(override val config: IosClientEngineConfig) : HttpClientEngine {
    // TODO: replace with UI dispatcher
    override val dispatcher: CoroutineDispatcher = Dispatchers.Unconfined

    override val coroutineContext: CoroutineContext = dispatcher + SupervisorJob()

    override suspend fun execute(
        data: HttpRequestData
    ): HttpResponseData = suspendCancellableCoroutine { continuation ->
        val callContext = coroutineContext + Job()
        val requestTime = GMTDate()

        val delegate = object : NSObject(), NSURLSessionDataDelegateProtocol {
            val chunks = Channel<ByteArray>(Channel.UNLIMITED)

            override fun URLSession(session: NSURLSession, dataTask: NSURLSessionDataTask, didReceiveData: NSData) {
                val content = didReceiveData.toByteArray()
                if (!chunks.offer(content)) throw IosHttpRequestException()
            }

            override fun URLSession(session: NSURLSession, task: NSURLSessionTask, didCompleteWithError: NSError?) {
                chunks.close()

                if (didCompleteWithError != null) {
                    continuation.resumeWithException(IosHttpRequestException(didCompleteWithError))
                    return
                }

                val rawResponse = task.response as NSHTTPURLResponse

                @Suppress("UNCHECKED_CAST")
                val headersDict = rawResponse.allHeaderFields as Map<String, String>

                val status = HttpStatusCode.fromValue(rawResponse.statusCode.toInt())
                val headers = buildHeaders {
                    headersDict.mapKeys { (key, value) -> append(key, value) }
                }

                val responseBody = writer(coroutineContext, autoFlush = true) {
                    while (!chunks.isClosedForReceive) {
                        val chunk = chunks.receive()
                        channel.writeFully(chunk)
                    }
                }.channel

                val version = HttpProtocolVersion.HTTP_1_1

                val response = HttpResponseData(
                    status, requestTime, headers, version,
                    responseBody, callContext
                )

                continuation.resume(response)
            }
        }

        val session = NSURLSession.sessionWithConfiguration(
            NSURLSessionConfiguration.defaultSessionConfiguration(),
            delegate, delegateQueue = NSOperationQueue.mainQueue()
        )

        val url = URLBuilder().takeFrom(data.url).buildString()
        val nativeRequest = NSMutableURLRequest.requestWithURL(NSURL(string = url))

        mergeHeaders(data.headers, data.body) { key, value ->
            nativeRequest.setValue(value, key)
        }

        nativeRequest.setCachePolicy(NSURLRequestReloadIgnoringCacheData)
        nativeRequest.setHTTPMethod(data.method.value)

        launch(callContext) {
            val content = data.body
            val body = when (content) {
                is OutgoingContent.ByteArrayContent -> content.bytes().toNSData()
                is OutgoingContent.WriteChannelContent -> writer(dispatcher) {
                    content.writeTo(channel)
                }.channel.readRemaining().readBytes().toNSData()
                is OutgoingContent.ReadChannelContent -> content.readFrom().readRemaining().readBytes().toNSData()
                is OutgoingContent.NoContent -> null
                else -> throw UnsupportedContentTypeException(content)
            }

            body?.let { nativeRequest.setHTTPBody(it) }

            config.requestConfig.let { nativeRequest.it() }
            session.dataTaskWithRequest(nativeRequest).resume()
        }
    }

    override fun close() {
        coroutineContext.cancel()
    }
}


