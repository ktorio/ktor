/*
 * Copyright 2014-2021 JetBrains s.r.o and contributors. Use of this source code is governed by the Apache 2.0 license.
 */

package io.ktor.network.sockets

import io.ktor.network.selector.*
import io.ktor.network.util.*
import io.ktor.utils.io.core.*
import io.ktor.utils.io.errors.*
import io.ktor.utils.io.pool.*
import kotlinx.atomicfu.*
import kotlinx.cinterop.*
import kotlinx.coroutines.*
import kotlinx.coroutines.channels.*
import kotlinx.coroutines.selects.*
import kotlinx.coroutines.sync.*
import kotlinx.io.IOException

private val CLOSED: (Throwable?) -> Unit = {}
private val CLOSED_INVOKED: (Throwable?) -> Unit = {}

internal class DatagramSendChannel(
    val descriptor: Int,
    val socket: DatagramSocketNative,
    val remote: SocketAddress?
) : SendChannel<Datagram> {
    private val onCloseHandler = atomic<((Throwable?) -> Unit)?>(null)
    private val closed = atomic(false)
    private val closedCause = atomic<Throwable?>(null)
    private val lock = Mutex()

    @DelicateCoroutinesApi
    override val isClosedForSend: Boolean
        get() = socket.isClosed

    override fun close(cause: Throwable?): Boolean {
        if (!closed.compareAndSet(false, true)) {
            return false
        }

        closedCause.value = cause

        if (!socket.isClosed) {
            socket.close()
        }

        closeAndCheckHandler()

        return true
    }

    @OptIn(InternalCoroutinesApi::class, UnsafeNumber::class)
    override fun trySend(element: Datagram): ChannelResult<Unit> {
        if (!lock.tryLock()) return ChannelResult.failure()
        if (remote != null) {
            check(element.address == remote) {
                "Datagram address ${element.address} doesn't match the connected address $remote"
            }
        }

        var result = false

        try {
            DefaultDatagramByteArrayPool.useInstance { buffer ->
                val length = element.packet.copy().readAvailable(buffer)
                val bytesWritten = sendto(element, buffer, length)

                result = when (bytesWritten) {
                    0 -> throw IOException("Failed writing to closed socket")
                    -1 -> {
                        if (isWouldBlockError(getSocketError())) {
                            false
                        } else {
                            throw PosixException.forSocketError()
                        }
                    }

                    else -> true
                }
            }
        } finally {
            lock.unlock()
        }

        if (result) {
            element.packet.close()
        }

        return ChannelResult.success(Unit)
    }

    override suspend fun send(element: Datagram) {
        if (remote != null) {
            check(element.address == remote) {
                "Datagram address ${element.address} doesn't match the connected address $remote"
            }
        }

        lock.withLock {
            DefaultDatagramByteArrayPool.useInstance { buffer ->
                val length = element.packet.readAvailable(buffer)
                sendSuspend(element, buffer, length)
            }
        }
    }

    @OptIn(ExperimentalForeignApi::class, UnsafeNumber::class)
    private fun sendto(datagram: Datagram, buffer: ByteArray, length: Int): Int {
        var bytesWritten: Int? = null
        buffer.usePinned { pinned ->
            if (remote == null) {
                datagram.address.address.nativeAddress { address, addressSize ->
                    bytesWritten = ktor_sendto(
                        descriptor,
                        pinned.addressOf(0),
                        length.convert(),
                        0,
                        address,
                        addressSize
                    ).toInt()
                }
            } else {
                bytesWritten = ktor_sendto(
                    descriptor,
                    pinned.addressOf(0),
                    length.convert(),
                    0,
                    null,
                    0.convert()
                ).toInt()
            }
        }
        return bytesWritten ?: error("bytesWritten cannot be null")
    }

    private tailrec suspend fun sendSuspend(
        datagram: Datagram,
        buffer: ByteArray,
        length: Int
    ) {
        val bytesWritten: Int = sendto(datagram, buffer, length)

        when (bytesWritten) {
            0 -> throw IOException("Failed writing to closed socket")
            -1 -> {
                if (isWouldBlockError(getSocketError())) {
                    socket.selector.select(socket.selectable, SelectInterest.WRITE)
                    sendSuspend(datagram, buffer, length)
                } else {
                    throw PosixException.forSocketError()
                }
            }
        }
    }

    override val onSend: SelectClause2<Datagram, SendChannel<Datagram>>
        get() = TODO("[DatagramSendChannel] doesn't support [onSend] select clause")

    @ExperimentalCoroutinesApi
    override fun invokeOnClose(handler: (cause: Throwable?) -> Unit) {
        if (onCloseHandler.compareAndSet(null, handler)) {
            return
        }

        if (onCloseHandler.value === CLOSED) {
            require(onCloseHandler.compareAndSet(CLOSED, CLOSED_INVOKED))
            handler(closedCause.value)
            return
        }

        failInvokeOnClose(onCloseHandler.value)
    }

    private fun closeAndCheckHandler() {
        while (true) {
            val handler = onCloseHandler.value
            if (handler === CLOSED_INVOKED) break
            if (handler == null) {
                if (onCloseHandler.compareAndSet(null, CLOSED)) break
                continue
            }

            require(onCloseHandler.compareAndSet(handler, CLOSED_INVOKED))
            handler(closedCause.value)
            break
        }
    }
}

private fun failInvokeOnClose(handler: ((cause: Throwable?) -> Unit)?) {
    val message = if (handler === CLOSED_INVOKED) {
        "Another handler was already registered and successfully invoked"
    } else {
        "Another handler was already registered: $handler"
    }

    throw IllegalStateException(message)
}
