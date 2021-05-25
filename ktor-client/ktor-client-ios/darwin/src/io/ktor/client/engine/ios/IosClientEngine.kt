/*
 * Copyright 2014-2019 JetBrains s.r.o and contributors. Use of this source code is governed by the Apache 2.0 license.
 */

package io.ktor.client.engine.ios

import io.ktor.client.engine.*
import io.ktor.client.features.*
import io.ktor.client.request.*
import io.ktor.http.*
import kotlinx.coroutines.*
import platform.Foundation.*
import kotlin.native.concurrent.*

internal class IosClientEngine(override val config: IosClientEngineConfig) : HttpClientEngineBase("ktor-ios") {

    override val dispatcher = Dispatchers.Unconfined

    override val supportedCapabilities = setOf(HttpTimeout)

    override suspend fun execute(data: HttpRequestData): HttpResponseData {
        val callContext = callContext()
        val responseReader = IosResponseReader(callContext, data, config)

        val configuration = NSURLSessionConfiguration.defaultSessionConfiguration().apply {
            setupProxy(config)
            config.sessionConfig(this)
        }

        val url = URLBuilder().takeFrom(data.url).buildString()

        val nativeRequest = NSMutableURLRequest.requestWithURL(NSURL(string = url)).apply {
            setupSocketTimeout(data)

            val content = data.body
            content.toNSData()?.let {
                setHTTPBody(it)
            }

            mergeHeaders(data.headers, data.body) { key, value ->
                setValue(value, key)
            }

            setCachePolicy(NSURLRequestReloadIgnoringCacheData)
            setHTTPMethod(data.method.value)

            config.requestConfig(this)
        }

        val session = NSURLSession.sessionWithConfiguration(
            configuration,
            responseReader.freeze(),
            delegateQueue = NSOperationQueue.currentQueue()
        )

        val task = session.dataTaskWithRequest(nativeRequest)

        launch(callContext) {
            task.resume()
        }

        callContext[Job]!!.invokeOnCompletion {
            session.finishTasksAndInvalidate()
        }

        return try {
            responseReader.awaitResponse()
        } catch (cause: CancellationException) {
            if (task.state == NSURLSessionTaskStateRunning) {
                task.cancel()
            }
            throw cause
        }
    }
}
