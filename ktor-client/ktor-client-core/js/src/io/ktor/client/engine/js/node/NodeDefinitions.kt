/*
 * Copyright 2014-2021 JetBrains s.r.o and contributors. Use of this source code is governed by the Apache 2.0 license.
 */

package io.ktor.client.engine.js.node

import io.ktor.client.engine.js.compatibility.*
import org.w3c.fetch.*
import kotlin.js.Promise

internal fun nodeFetch(url: String, init: RequestInit? = null): Promise<Response> {
    val fetch = js("eval('require')('node-fetch')")
    return fetch(url, init).unsafeCast<Promise<Response>>()
}

internal interface NodeHeaders

internal operator fun NodeHeaders.set(name: String, value: String) {
    this.asDynamic()[name] = value
}

internal fun NodeAbortController(): AbortController {
    @Suppress("UNUSED_VARIABLE")
    val controller = js("eval('require')('abort-controller')")
    return js("new controller()").unsafeCast<AbortController>()
}

@Suppress("UNUSED_VARIABLE")
internal fun NodeWebsocket(url: String, protocols: dynamic = null, headers: Map<String, List<String>>): dynamic {
    val headersJS = Unit.unsafeCast<NodeHeaders>().apply {
        headers.forEach { (name, value) ->
            this[name] = value.joinToString(",")
        }
    }

    val WebSocket = js("eval('require')('ws')")
    return js("new WebSocket('ws://' + url, protocols, {'headers': headersJS})")
}
