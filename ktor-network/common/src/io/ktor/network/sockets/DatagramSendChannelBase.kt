/*
 * Copyright 2014-2026 JetBrains s.r.o and contributors. Use of this source code is governed by the Apache 2.0 license.
 */

package io.ktor.network.sockets

import kotlinx.atomicfu.*
import kotlinx.coroutines.*
import kotlinx.coroutines.channels.*
import kotlinx.coroutines.selects.*
import kotlinx.coroutines.sync.*

private val CLOSED: (Throwable?) -> Unit = {}
private val CLOSED_INVOKED: (Throwable?) -> Unit = {}

internal abstract class DatagramSendChannelBase(
    private val socket: ASocket,
    protected val remote: SocketAddress?,
) : SendChannel<Datagram> {
    private val lock = Mutex()
    private val onCloseHandler = atomic<((Throwable?) -> Unit)?>(null)
    private val closed = atomic(false)
    private val closedCause = atomic<Throwable?>(null)

    @DelicateCoroutinesApi
    override val isClosedForSend: Boolean
        get() = closed.value

    override val onSend: SelectClause2<Datagram, SendChannel<Datagram>>
        get() = TODO("[DatagramSendChannel] doesn't support [onSend] select clause")

    protected abstract fun trySendImpl(element: Datagram): Boolean
    protected abstract suspend fun sendImpl(element: Datagram)

    @OptIn(InternalCoroutinesApi::class)
    final override fun trySend(element: Datagram): ChannelResult<Unit> {
        if (closed.value) return ChannelResult.closed(closedCause.value)
        checkRemoteAddress(element)

        if (!lock.tryLock()) return ChannelResult.failure()
        val result = try {
            trySendImpl(element)
        } finally {
            lock.unlock()
        }
        return when (result) {
            true -> ChannelResult.success(Unit)
            false -> ChannelResult.failure()
        }
    }

    final override suspend fun send(element: Datagram) {
        if (closed.value) throw closedCause.value ?: ClosedSendChannelException("Channel was closed for send")
        checkRemoteAddress(element)

        lock.withLock {
            sendImpl(element)
        }
    }

    private fun checkRemoteAddress(element: Datagram) {
        val remote = remote ?: return
        check(element.address == remote) {
            "Datagram address ${element.address} doesn't match the connected address $remote"
        }
    }

    final override fun close(cause: Throwable?): Boolean {
        if (!closed.compareAndSet(false, true)) {
            return false
        }

        closedCause.value = cause

        if (!socket.isClosed) {
            // should not throw
            socket.close()
        }

        closeAndCheckHandler()

        return true
    }

    @ExperimentalCoroutinesApi
    final override fun invokeOnClose(handler: (cause: Throwable?) -> Unit) {
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

    private fun failInvokeOnClose(handler: ((cause: Throwable?) -> Unit)?) {
        val message = if (handler === CLOSED_INVOKED) {
            "Another handler was already registered and successfully invoked"
        } else {
            "Another handler was already registered: $handler"
        }

        throw IllegalStateException(message)
    }
}
