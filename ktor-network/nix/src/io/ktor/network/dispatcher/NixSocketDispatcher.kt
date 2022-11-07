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
public actual fun SocketDispatcher(): SocketDispatcher = NixSocketDispatcher()

@Suppress("FunctionName")
public actual fun SocketDispatcher(parent: CoroutineContext): SocketDispatcher = NixSocketDispatcher(parent)
public class NixSocketDispatcher(parent: CoroutineContext = Dispatchers.Unconfined) : SocketDispatcher {
    private val associatedJob: CompletableJob = Job(parent[Job])
    override val coroutineContext: CoroutineContext = parent + associatedJob
    internal val selector = WorkerSelectorManager(coroutineContext)
    override fun tcp(): TcpSocketBuilder = NixTcpSocketBuilder(this)
    override fun udp(): UdpSocketBuilder = NixUdpSocketBuilder(this)
    override fun close() {
        associatedJob.complete()
    }
}
