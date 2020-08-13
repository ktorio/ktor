/*
 * Copyright 2014-2019 JetBrains s.r.o and contributors. Use of this source code is governed by the Apache 2.0 license.
 */

package io.ktor.network.tls

import io.ktor.network.sockets.*
import kotlin.coroutines.*

/**
 * Make [Socket] connection secure with TLS using [TLSConfig].
 */
expect suspend fun Socket.tls(
    coroutineContext: CoroutineContext, config: TLSConfig
): Socket

/**
 * Make [Socket] connection secure with TLS configured with [block].
 *
 * TODO: report YT issue
 */
suspend fun Socket.tls(coroutineContext: CoroutineContext): Socket =  tls(coroutineContext) {}

/**
 * Make [Socket] connection secure with TLS configured with [block].
 */
expect suspend fun Socket.tls(coroutineContext: CoroutineContext, block: TLSConfigBuilder.() -> Unit): Socket
