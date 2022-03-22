/*
 * Copyright 2014-2022 JetBrains s.r.o and contributors. Use of this source code is governed by the Apache 2.0 license.
 */

package io.ktor.network.tls

import io.ktor.network.sockets.*
import io.ktor.util.*
import kotlin.coroutines.*

@InternalAPI
public expect fun SslContext(builder: TLSConfigBuilder): SslContext

@InternalAPI
public expect class SslContext() {
    public fun createClientEngine(): SslEngine
    public fun createClientEngine(peerHost: String, peerPort: Int): SslEngine

    //TODO: client auth type
    public fun createServerEngine(): SslEngine
}

@InternalAPI
public expect abstract class SslEngine

@InternalAPI
public expect fun Socket.ssl(
    coroutineContext: CoroutineContext,
    engine: SslEngine
): Socket

@InternalAPI
public expect fun Connection.ssl(
    coroutineContext: CoroutineContext,
    engine: SslEngine
): Connection
