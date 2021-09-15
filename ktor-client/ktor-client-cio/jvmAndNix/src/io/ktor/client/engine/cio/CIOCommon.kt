// ktlint-disable filename
/*
* Copyright 2014-2021 JetBrains s.r.o and contributors. Use of this source code is governed by the Apache 2.0 license.
*/

package io.ktor.client.engine.cio

import io.ktor.client.engine.*
import io.ktor.util.*

/**
 * [HttpClientEngineFactory] using a Coroutine based I/O implementation without additional dependencies
 * with the the associated configuration [CIOEngineConfig].
 *
 * Just supports HTTP/1.x and HTTPS requests.
 */
public object CIO : HttpClientEngineFactory<CIOEngineConfig> {
    init {
        addToLoader()
    }

    override fun create(block: CIOEngineConfig.() -> Unit): HttpClientEngine =
        CIOEngine(CIOEngineConfig().apply(block))

    override fun toString(): String = "CIO"
}
