/*
 * Copyright 2014-2022 JetBrains s.r.o and contributors. Use of this source code is governed by the Apache 2.0 license.
 */

package io.ktor.network.dispatcher

import io.ktor.network.dispatcher.*
import io.ktor.network.selector.*
import io.ktor.network.sockets.*
import kotlinx.coroutines.*
import kotlin.coroutines.*

@Suppress("FunctionName")
public actual fun SocketDispatcher(): SocketDispatcher = JvmSocketDispatcher()

@Suppress("FunctionName")
public actual fun SocketDispatcher(parent: CoroutineContext): SocketDispatcher = JvmSocketDispatcher(parent)
public class JvmSocketDispatcher(parent: CoroutineContext = Dispatchers.IO) : SocketDispatcher {
    private val associatedJob: CompletableJob = Job(parent[Job])
    override val coroutineContext: CoroutineContext = parent + associatedJob
    internal val selector = ActorSelectorManager(coroutineContext)
    override fun tcp(): TcpSocketBuilder = JvmTcpSocketBuilder(this)
    override fun udp(): UdpSocketBuilder = JvmUdpSocketBuilder(this)
    override fun close() {
        associatedJob.complete()
    }
}
