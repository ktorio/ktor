/*
 * Copyright 2014-2019 JetBrains s.r.o and contributors. Use of this source code is governed by the Apache 2.0 license.
 */

package io.ktor.client.engine.js

import io.ktor.client.engine.*

/**
 * A JavaScript client engine that uses the fetch API to execute requests.
 *
 * To create the client with this engine, pass it to the `HttpClient` constructor:
 * ```kotlin
 * val client = HttpClient(Js)
 * ```
 * You can also call the [JsClient] function to get the [Js] engine singleton:
 * ```kotlin
 * val client = JsClient()
 * ```
 *
 * You can learn more about client engines from [Engines](https://ktor.io/docs/http-client-engines.html).
 */
public object Js : HttpClientEngineFactory<JsClientEngineConfig> {
    override fun create(block: JsClientEngineConfig.() -> Unit): HttpClientEngine =
        JsClientEngine(JsClientEngineConfig().apply(block))
}


public open class JsClientEngineConfig : HttpClientEngineConfig() {
    public var nodeOptions: dynamic = js("Object").create(null)
}

/**
 * Creates a [Js] client engine.
 */
@JsName("JsClient")
public fun JsClient(): HttpClientEngineFactory<JsClientEngineConfig> = Js
