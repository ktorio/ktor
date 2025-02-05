/*
 * Copyright 2014-2019 JetBrains s.r.o and contributors. Use of this source code is governed by the Apache 2.0 license.
 */

package io.ktor.network.sockets

import io.ktor.network.selector.*
import io.ktor.network.util.*
import io.ktor.utils.io.errors.PosixException
import kotlinx.atomicfu.atomic
import kotlinx.cinterop.*
import kotlinx.coroutines.*
import kotlinx.io.*
import platform.posix.*
import kotlin.coroutines.*

@OptIn(ExperimentalForeignApi::class)
internal class TCPServerSocketNative(
    override val descriptor: Int,
    private val selector: SelectorManager,
    override val localAddress: SocketAddress,
    parent: CoroutineContext = EmptyCoroutineContext
) : SelectableBase(), ServerSocket, CoroutineScope {
    private val _socketContext: CompletableJob = SupervisorJob(parent[Job])

    override val coroutineContext: CoroutineContext = parent + Dispatchers.Unconfined + _socketContext

    override val socketContext: Job
        get() = _socketContext

    private val closeFlag = atomic(false)

    init {
        signalIgnoreSigpipe()
    }

    @Suppress("DUPLICATE_LABEL_IN_WHEN")
    override suspend fun accept(): Socket = memScoped {
        val clientAddress = alloc<sockaddr_storage>()
        val clientAddressLength: UIntVarOf<UInt> = alloc()
        clientAddressLength.value = sizeOf<sockaddr_storage>().convert()

        var clientDescriptor: Int
        while (true) {
            clientDescriptor = ktor_accept(descriptor, clientAddress.ptr.reinterpret(), clientAddressLength.ptr)
            if (clientDescriptor > 0) {
                break
            }

            val error = getSocketError()
            when {
                isWouldBlockError(error) -> {
                    selector.select(this@TCPServerSocketNative, SelectInterest.ACCEPT)
                }
                else -> throw PosixException.forSocketError(error)
            }
        }
        buildOrCloseSocket(clientDescriptor) {
            nonBlocking(clientDescriptor).check()

            val remoteAddress = clientAddress.reinterpret<sockaddr>().toNativeSocketAddress()

            TCPSocketNative(
                selector,
                clientDescriptor,
                remoteAddress = remoteAddress.toSocketAddress(),
                parent = selfContext() + coroutineContext
            )
        }
    }

    override fun close() {
        if (!closeFlag.compareAndSet(false, true)) return

        ktor_shutdown(descriptor, ShutdownCommands.Both)
        // Close select call must happen before notifyClosed, so run undispatched.
        launch(start = CoroutineStart.UNDISPATCHED) {
            // SelectorManager could throw exception if it is closed, ignore it as notifyClosed
            // will still close the descriptor as expected.
            try {
                selector.select(this@TCPServerSocketNative, SelectInterest.CLOSE)
            } catch (_: IOException) {
            }
        }
        selector.notifyClosed(this)
        _socketContext.complete()
    }
}

private suspend inline fun selfContext(): CoroutineContext = coroutineContext
