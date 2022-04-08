/*
 * Copyright 2014-2022 JetBrains s.r.o and contributors. Use of this source code is governed by the Apache 2.0 license.
 */

package io.ktor.network.tls

import io.ktor.network.sockets.*
import io.ktor.network.tls.internal.openssl.*
import kotlin.coroutines.*

public actual suspend fun Connection.tls(
    coroutineContext: CoroutineContext,
    config: TLSConfig
): Socket {
    val ssl = SSL_new(config.sslContext)!!
    when (val address = socket.remoteAddress) {
        is UnixSocketAddress -> {}
        is InetSocketAddress -> SSL_set1_host(ssl, "${address.hostname}:${address.port}")
    }

    //TODO: support setting serverName

    when (config.isClient) {
        true -> SSL_set_connect_state(ssl)
        false -> SSL_set_accept_state(ssl)
    }

    return SSLSocket(coroutineContext, ssl, this)
}
