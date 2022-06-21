/*
 * Copyright 2014-2022 JetBrains s.r.o and contributors. Use of this source code is governed by the Apache 2.0 license.
 */

package io.ktor.network.tls

import io.ktor.network.sockets.*
import io.ktor.network.tls.internal.openssl.*
import kotlinx.cinterop.*
import kotlin.coroutines.*

public actual suspend fun Connection.tls(
    coroutineContext: CoroutineContext,
    config: TLSConfig
): Socket {
    val ssl = SSL_new(config.sslContext)!!

    when (val address = socket.remoteAddress) {
        is UnixSocketAddress -> {}
        is InetSocketAddress -> check(
            SSL_set1_host(ssl, config.serverName ?: "${address.hostname}:${address.port}") == 1
        ) { "Failed to set host name" }
    }

    //TODO: is it correct??
    if (config.serverName != null) {
        //TODO: extract macros to def file
        check(
            SSL_ctrl(
                ssl,
                SSL_CTRL_SET_TLSEXT_HOSTNAME,
                TLSEXT_NAMETYPE_host_name.toLong(),
                config.serverName.refTo(0)
            ) == 1L
        ) { "Failed to set server name" }
    }

    when (config.isClient) {
        true -> SSL_set_connect_state(ssl)
        false -> SSL_set_accept_state(ssl)
    }

    return SSLSocket(coroutineContext, ssl, this)
}
