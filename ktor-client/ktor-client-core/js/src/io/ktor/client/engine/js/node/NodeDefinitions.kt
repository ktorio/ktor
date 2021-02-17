/*
 * Copyright 2014-2021 JetBrains s.r.o and contributors. Use of this source code is governed by the Apache 2.0 license.
 */

package io.ktor.client.engine.js.node

import io.ktor.client.engine.js.compatibility.*
import org.w3c.dom.*
import org.w3c.fetch.*
import kotlin.js.*

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
