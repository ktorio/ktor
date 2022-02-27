/*
 * Copyright 2014-2019 JetBrains s.r.o and contributors. Use of this source code is governed by the Apache 2.0 license.
 */

package io.ktor.client.engine.darwin

import io.ktor.client.engine.*
import io.ktor.util.*
import platform.Foundation.*

@Suppress("DEPRECATION")
@OptIn(ExperimentalStdlibApi::class)
@EagerInitialization
private val initHook = Darwin

/**
 * [HttpClientEngineFactory] using a [NSURLRequest] in implementation
 * with the associated requestConfig [HttpClientEngineConfig].
 */
@OptIn(InternalAPI::class)
public object Darwin : HttpClientEngineFactory<DarwinClientEngineConfig> {
    init {
        engines.append(this)
    }

    override fun create(block: DarwinClientEngineConfig.() -> Unit): HttpClientEngine =
        DarwinClientEngine(DarwinClientEngineConfig().apply(block))

    override fun toString(): String = "Darwin"
}
