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
import platform.posix.*

private val CLOSED: (Throwable?) -> Unit = {}
private val CLOSED_INVOKED: (Throwable?) -> Unit = {}

internal class DatagramSendChannel(
    val descriptor: Int,
    val socket: DatagramSocketNative
) : SendChannel<Datagram> {
    private val onCloseHandler = atomic<((Throwable?) -> Unit)?>(null)
    private val closed = atomic(false)
    private val closedCause = atomic<Throwable?>(null)
    private val lock = Mutex()

    @ExperimentalCoroutinesApi
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

        var result = false

        try {
            DefaultDatagramChunkBufferPool.useInstance { buffer ->
                element.packet.copy().readAvailable(buffer)

                val bytes = element.packet.copy().readBytes()
                var bytesWritten: Int? = null
                bytes.usePinned { pinned ->
                    element.address.address.nativeAddress { address, addressSize ->
                        bytesWritten = sendto(
                            descriptor,
                            pinned.addressOf(0),
                            bytes.size.convert(),
                            0,
                            address,
                            addressSize
                        ).toInt()
                    }
                }
                result = when (bytesWritten ?: error("bytesWritten cannot be null")) {
                    0 -> throw IOException("Failed writing to closed socket")
                    -1 -> {
                        if (errno == EAGAIN) {
                            false
                        } else {
                            throw PosixException.forErrno()
                        }
                    }
                    else -> true
                }
            }
        } finally {
            lock.unlock()
        }

        if (result) {
            element.packet.release()
        }

        return ChannelResult.success(Unit)
    }

    override suspend fun send(element: Datagram) {
        lock.withLock {
            sendImpl(element)
        }
    }

    @OptIn(UnsafeNumber::class)
    private tailrec suspend fun sendImpl(
        datagram: Datagram,
        bytes: ByteArray = datagram.packet.readBytes()
    ) {
        var bytesWritten: Int? = null
        bytes.usePinned { pinned ->
            datagram.address.address.nativeAddress { address, addressSize ->
                bytesWritten = sendto(
                    descriptor,
                    pinned.addressOf(0),
                    bytes.size.convert(),
                    0,
                    address,
                    addressSize
                ).toInt()
            }
        }
        when (bytesWritten ?: error("bytesWritten cannot be null")) {
            0 -> throw IOException("Failed writing to closed socket")
            -1 -> {
                if (errno == EAGAIN) {
                    socket.selector.select(socket.selectable, SelectInterest.WRITE)
                    sendImpl(datagram, bytes)
                } else {
                    throw PosixException.forErrno()
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
