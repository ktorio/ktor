/*
 * Copyright 2014-2021 JetBrains s.r.o and contributors. Use of this source code is governed by the Apache 2.0 license.
 */

package io.ktor.client.engine.js

import io.ktor.client.engine.*
import io.ktor.util.*

private val initHook = Js

/**
 * [HttpClientEngineFactory] using a node or browser fetch API depending on the current platform to execute requests.
 */
public object Js : HttpClientEngineFactory<HttpClientEngineConfig> {

    init {
        engines.add(0, this)
    }

    override fun create(block: HttpClientEngineConfig.() -> Unit): HttpClientEngine =
        JsClientEngine(HttpClientEngineConfig().apply(block), if (PlatformUtils.IS_BROWSER) Browser else Node)
}
