/*
 * Copyright 2014-2019 JetBrains s.r.o and contributors. Use of this source code is governed by the Apache 2.0 license.
 */

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
import io.ktor.utils.io.*
import io.ktor.utils.io.core.*
import platform.CFNetwork.*
import platform.Foundation.*
import platform.darwin.*
import kotlin.coroutines.*

internal class IosClientEngine(override val config: IosClientEngineConfig) : HttpClientEngineBase("ktor-ios") {
    // TODO: replace with UI dispatcher
    override val dispatcher = Dispatchers.Unconfined

    override suspend fun execute(data: HttpRequestData): HttpResponseData {
        val callContext = callContext()!!

        return suspendCancellableCoroutine { continuation ->
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

            val configuration = NSURLSessionConfiguration.defaultSessionConfiguration()
            configuration.setupProxy()
            config.sessionConfig(configuration)

            val session = NSURLSession.sessionWithConfiguration(
                configuration,
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

                config.requestConfig(nativeRequest)
                session.dataTaskWithRequest(nativeRequest).resume()
            }
        }
    }

    private fun NSURLSessionConfiguration.setupProxy() {
        val proxy = config.proxy ?: return
        val url = proxy.url

        val type = when (url.protocol) {
            URLProtocol.HTTP -> kCFProxyTypeHTTP
            URLProtocol.HTTPS -> kCFProxyTypeHTTPS
            URLProtocol.SOCKS -> kCFProxyTypeSOCKS
            else -> error("Proxy type ${url.protocol.name} is unsupported by iOS client engine.")
        }

        val port = url.port.toString()
        connectionProxyDictionary = mapOf(
            kCFProxyHostNameKey to url.host,
            kCFProxyPortNumberKey to port,
            kCFProxyTypeKey to type
        )
    }
}


