/*
* Copyright 2014-2021 JetBrains s.r.o and contributors. Use of this source code is governed by the Apache 2.0 license.
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
public actual suspend fun Socket.tls(
    coroutineContext: CoroutineContext,
    config: TLSConfig
): Socket {
    val reader = openReadChannel()
    val writer = openWriteChannel()

    return try {
        openTLSSession(this, reader, writer, config, coroutineContext)
    } catch (cause: Throwable) {
        reader.cancel(cause)
        writer.close(cause)
        close()
        throw cause
    }
}

/**
 * Make [Socket] connection secure with TLS configured with [block].
 *
 * [Report a problem](https://ktor.io/feedback/?fqname=io.ktor.network.tls.tls)
 */
public actual suspend fun Socket.tls(coroutineContext: CoroutineContext, block: TLSConfigBuilder.() -> Unit): Socket =
    tls(coroutineContext, TLSConfigBuilder().apply(block).build())
