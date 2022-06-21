/*
* Copyright 2014-2021 JetBrains s.r.o and contributors. Use of this source code is governed by the Apache 2.0 license.
*/

package io.ktor.network.tls

import io.ktor.network.sockets.*
import kotlin.coroutines.*

/**
 * Make [Socket] connection secure with TLS using [TLSConfig].
 */
public suspend fun Socket.tls(
    coroutineContext: CoroutineContext,
    config: TLSConfig
): Socket = connection().tls(coroutineContext, config)

/**
 * Make [Socket] connection secure with TLS configured with [block].
 */
public suspend fun Socket.tls(
    coroutineContext: CoroutineContext,
    isClient: Boolean = true,
    block: TLSConfigBuilder.() -> Unit = {}
): Socket = tls(coroutineContext, createTLSConfig(isClient, block))

/**
 * Make [Socket] connection secure with TLS using [TLSConfig].
 */
public expect suspend fun Connection.tls(
    coroutineContext: CoroutineContext,
    config: TLSConfig
): Socket

/**
 * Make [Socket] connection secure with TLS configured with [block].
 */
public suspend fun Connection.tls(
    coroutineContext: CoroutineContext,
    isClient: Boolean = true,
    block: TLSConfigBuilder.() -> Unit
): Socket = tls(coroutineContext, socket.createTLSConfig(isClient, block))

private fun Socket.createTLSConfig(
    isClient: Boolean = true,
    block: TLSConfigBuilder.() -> Unit
): TLSConfig {
    val config = TLSConfig(isClient, block)
    socketContext.invokeOnCompletion {
        config.close()
    }
    return config
}
