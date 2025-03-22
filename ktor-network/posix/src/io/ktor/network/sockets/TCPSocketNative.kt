/*
 * Copyright 2014-2024 JetBrains s.r.o and contributors. Use of this source code is governed by the Apache 2.0 license.
 */

package io.ktor.network.sockets

import io.ktor.network.selector.*
import io.ktor.network.util.*
import io.ktor.utils.io.errors.*
import kotlinx.cinterop.*
import platform.posix.*
import kotlin.coroutines.*

internal class TCPSocketNative(
    private val selector: SelectorManager,
    descriptor: Int,
    override val remoteAddress: SocketAddress,
    parent: CoroutineContext = EmptyCoroutineContext
) : NativeSocketImpl(selector, descriptor, parent), Socket {

    override val localAddress: SocketAddress
        get() = getLocalAddress(descriptor).toSocketAddress()

    @OptIn(ExperimentalForeignApi::class)
    internal suspend fun connect(target: NativeSocketAddress): Socket {
        memScoped {
            var connectResult = -1
            target.nativeAddress { address, size ->
                connectResult = ktor_connect(descriptor, address, size)
            }

            val error = getSocketError()
            when {
                connectResult >= 0 -> {}
                isWouldBlockError(error) -> {
                    while (true) {
                        selector.select(this@TCPSocketNative, SelectInterest.CONNECT)
                        val result = alloc<IntVar>()
                        val size = alloc<UIntVar> {
                            value = sizeOf<IntVar>().convert()
                        }
                        ktor_getsockopt(descriptor, SOL_SOCKET, SO_ERROR, result.ptr, size.ptr).check()
                        val resultValue = result.value.toInt()
                        when {
                            resultValue == 0 -> break // connected
                            isWouldBlockError(resultValue) -> continue
                            else -> throw PosixException.forSocketError(error = resultValue)
                        }
                    }
                }
                else -> throw PosixException.forSocketError(error)
            }
        }
        return this
    }
}
