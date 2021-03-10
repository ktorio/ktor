/*
 * Copyright 2014-2021 JetBrains s.r.o and contributors. Use of this source code is governed by the Apache 2.0 license.
 */

package io.ktor.client.engine.js

import io.ktor.client.engine.*

private val initHook = JsNode

/**
 * [HttpClientEngineFactory] using a node fetch API to execute requests.
 */
public object JsNode : HttpClientEngineFactory<HttpClientEngineConfig> {

    init {
        engines.add(this)
    }

    override fun create(block: HttpClientEngineConfig.() -> Unit): HttpClientEngine =
        JsClientEngine(HttpClientEngineConfig().apply(block), Node)
}
