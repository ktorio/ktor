package io.ktor.client.engine.ios

import io.ktor.client.call.*
import io.ktor.client.engine.*
import io.ktor.client.request.*
import io.ktor.client.utils.*
import io.ktor.http.*
import io.ktor.http.content.*
import io.ktor.util.date.*
import kotlinx.coroutines.experimental.*
import kotlinx.coroutines.experimental.channels.*
import kotlinx.coroutines.experimental.io.*
import kotlinx.io.core.*
import platform.Foundation.*
import platform.darwin.*

class IosClientEngine(override val config: HttpClientEngineConfig) : HttpClientEngine {
    private val context: Job = Job()

    // TODO: replace with UI dispatcher
    override val dispatcher: CoroutineDispatcher = config.dispatcher ?: Unconfined

    override suspend fun execute(
        call: HttpClientCall,
        data: HttpRequestData
    ): HttpEngineCall = suspendCancellableCoroutine { continuation ->
        val request = DefaultHttpRequest(call, data)
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

                val response = task.response as NSHTTPURLResponse

                @Suppress("UNCHECKED_CAST")
                val headersDict = response.allHeaderFields as Map<String, String>

                val status = HttpStatusCode.fromValue(response.statusCode.toInt())
                val headers = buildHeaders {
                    headersDict.mapKeys { (key, value) -> append(key, value) }
                }

                val responseContext = writer(dispatcher, autoFlush = true) {
                    while (!chunks.isClosedForReceive) {
                        val chunk = chunks.receive()
                        channel.writeFully(chunk)
                    }
                }

                val result = IosHttpResponse(
                    call, status, headers, requestTime,
                    responseContext.channel, responseContext
                )

                continuation.resume(HttpEngineCall(request, result))
            }
        }

        val session = NSURLSession.sessionWithConfiguration(
            NSURLSessionConfiguration.defaultSessionConfiguration(),
            delegate, delegateQueue = NSOperationQueue.mainQueue()
        )

        val url = URLBuilder().takeFrom(request.url).buildString()
        val nativeRequest = NSMutableURLRequest.requestWithURL(NSURL(string = url))
        val headers = request.headers.entries()
        val contentHeaders = request.content.headers.entries()
        val contentType = request.content.contentType

        headers.forEach { (key, values) ->
            values.forEach { nativeRequest.setValue(it, key) }
        }

        contentHeaders.forEach { (key, values) ->
            values.forEach { nativeRequest.setValue(it, key) }
        }

        nativeRequest.setValue(HttpHeaders.ContentType, contentType.toString())

        nativeRequest.setHTTPMethod(request.method.value)

        launch(dispatcher) {
            val content = request.content
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
            session.dataTaskWithRequest(nativeRequest).resume()
        }
    }

    override fun close() {
        context.cancel()
    }
}


