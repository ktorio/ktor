/*
 * Copyright 2014-2022 JetBrains s.r.o and contributors. Use of this source code is governed by the Apache 2.0 license.
 */

package io.ktor.network.dispatcher

import io.ktor.network.sockets.*
import io.ktor.utils.io.core.*
import kotlinx.coroutines.*
import kotlin.coroutines.*

@Suppress("FunctionName")
public expect fun SocketDispatcher(): SocketDispatcher

@Suppress("FunctionName")
public expect fun SocketDispatcher(parent: CoroutineContext): SocketDispatcher
public interface SocketDispatcher : CoroutineScope, Closeable {
    public fun tcp(): TcpSocketBuilder
    public fun udp(): UdpSocketBuilder
}
