/*
 * Copyright 2014-2019 JetBrains s.r.o and contributors. Use of this source code is governed by the Apache 2.0 license.
 */

package io.ktor.client.engine.jetty

import io.ktor.client.engine.*
import io.ktor.client.request.*
import kotlinx.coroutines.*
import org.eclipse.jetty.http2.client.*
import org.eclipse.jetty.util.thread.*

internal class JettyHttp2Engine(
    override val config: JettyEngineConfig
) : HttpClientJvmEngine("ktor-jetty") {
    private val jettyClient = HTTP2Client().apply {
        addBean(config.sslContextFactory)
        check(config.proxy == null) { "Proxy unsupported in Jetty engine." }

        executor = QueuedThreadPool().apply {
            name = "ktor-jetty-client-qtp"
        }

        start()
    }

    override suspend fun execute(data: HttpRequestData): HttpResponseData {
        val callContext = createCallContext()
        return try {
            data.executeRequest(jettyClient, config, callContext)
        } catch (cause: Throwable) {
            (callContext[Job] as? CompletableJob)?.completeExceptionally(cause)
            throw cause
        }
    }

    override fun close() {
        super.close()
        coroutineContext[Job]?.invokeOnCompletion {
            jettyClient.stop()
        }
    }
}
