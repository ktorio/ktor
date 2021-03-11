/*
 * Copyright 2014-2021 JetBrains s.r.o and contributors. Use of this source code is governed by the Apache 2.0 license.
 */

package io.ktor.client.engine.js

import io.ktor.client.engine.js.compatibility.*
import io.ktor.utils.io.*
import kotlinx.coroutines.*
import kotlinx.coroutines.channels.*
import org.khronos.webgl.*
import org.w3c.dom.*
import org.w3c.fetch.*
import kotlin.js.Promise

/**
 * Node JS API wrapper
 */
public object Node : JsPlatformApi {

    override fun fetch(url: String): Promise<Response> = nodeFetch(url)

    override fun fetch(url: String, init: RequestInit): Promise<Response> = nodeFetch(url, init)

    override fun createAbortController(): AbortController = NodeAbortController()

    override fun createHeaders(): Headers = NodeFetch.Headers()

    override fun createWebSocket(urlString: String): WebSocket = NodeWebsocket(urlString)

    override fun readBody(scope: CoroutineScope, response: Response): ByteReadChannel {
        val body: dynamic = response.body ?: error("Fail to get body")
        return scope.bodyFromDynamic(body)
    }
}

private fun CoroutineScope.bodyFromDynamic(body: dynamic): ByteReadChannel = writer {
    val responseData = Channel<ByteArray>(1)

    body.on("data") { chunk: ArrayBuffer ->
        responseData.offer(Uint8Array(chunk).asByteArray())
        body.pause()
    }

    body.on("error") { error ->
        val cause = JsError(error)
        responseData.close(cause)
        channel.close(cause)
    }

    body.on("end") {
        responseData.close()
    }

    try {
        for (chunk in responseData) {
            channel.writeFully(chunk)
            body.resume()
        }
    } catch (cause: Throwable) {
        body.destroy(cause)
        throw cause
    }
}.channel

@JsModule("node-fetch")
@JsNonModule
internal external fun nodeFetch(url: String, init: RequestInit = definedExternally): Promise<Response>

@JsModule("node-fetch")
@JsNonModule
internal external object NodeFetch {
    class Headers : org.w3c.fetch.Headers
}

@JsModule("abort-controller")
@JsNonModule
@JsName("AbortController")
internal external class NodeAbortController : AbortController

@JsModule("ws")
@JsNonModule
internal external class NodeWebsocket(url: String, protocols: dynamic = definedExternally) : WebSocket
