/*
 * Copyright 2014-2024 JetBrains s.r.o and contributors. Use of this source code is governed by the Apache 2.0 license.
 */

package io.ktor.network.tls

import io.ktor.network.sockets.*
import io.ktor.utils.io.*
import kotlin.coroutines.*

/**
 * Make [Socket] connection secure with TLS using [TLSConfig].
 *
 * [Report a problem](https://ktor.io/feedback/?fqname=io.ktor.network.tls.tls)
 */
public expect suspend fun Socket.tls(
    coroutineContext: CoroutineContext,
    config: TLSConfig
): Socket

/**
 * Make [Socket] connection secure with TLS.
 *
 * TODO: report YT issue
 *
 * [Report a problem](https://ktor.io/feedback/?fqname=io.ktor.network.tls.tls)
 */
public suspend fun Socket.tls(coroutineContext: CoroutineContext): Socket = tls(coroutineContext) {}

/**
 * Make [Socket] connection secure with TLS configured with [block].
 *
 * [Report a problem](https://ktor.io/feedback/?fqname=io.ktor.network.tls.tls)
 */
public expect suspend fun Socket.tls(coroutineContext: CoroutineContext, block: TLSConfigBuilder.() -> Unit): Socket

/**
 * Make [Socket] connection secure with TLS using [TLSConfig].
 *
 * [Report a problem](https://ktor.io/feedback/?fqname=io.ktor.network.tls.tls)
 */
public suspend fun Connection.tls(
    coroutineContext: CoroutineContext,
    config: TLSConfig
): Socket {
    return try {
        openTLSSession(socket, input, output, config, coroutineContext)
    } catch (cause: Throwable) {
        input.cancel(cause)
        output.close(cause)
        socket.close()
        throw cause
    }
}

/**
 * Make [Socket] connection secure with TLS.
 *
 * [Report a problem](https://ktor.io/feedback/?fqname=io.ktor.network.tls.tls)
 */
public suspend fun Connection.tls(coroutineContext: CoroutineContext): Socket = tls(coroutineContext) {}

/**
* Make [Socket] connection secure with TLS configured with [block].
*
* [Report a problem](https://ktor.io/feedback/?fqname=io.ktor.network.tls.tls)
*/
public suspend fun Connection.tls(coroutineContext: CoroutineContext, block: TLSConfigBuilder.() -> Unit): Socket =
    tls(coroutineContext, TLSConfigBuilder().apply(block).build())
