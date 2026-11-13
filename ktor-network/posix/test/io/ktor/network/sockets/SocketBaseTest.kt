/*
 * Copyright 2014-2026 JetBrains s.r.o and contributors. Use of this source code is governed by the Apache 2.0 license.
 */

package io.ktor.network.sockets

import io.ktor.test.*
import io.ktor.utils.io.*
import kotlinx.coroutines.*
import kotlinx.io.IOException
import kotlin.coroutines.*
import kotlin.test.*

class SocketBaseTest {
    @Test
    fun `close after the socket context is cancelled still closes the descriptor`() = runTest {
        val socket = TestSocket { error("No channel in this test") }
        socket.socketContext.cancelAndJoin()

        socket.close()
        assertFailsWith<kotlinx.coroutines.CancellationException> { socket.awaitClosed() }

        assertEquals(1, socket.closeCount)
    }

    @Test
    fun `close waits for a channel that is still being attached`() = runTest {
        var closesDuringAttachment = -1
        val socket = TestSocket { channel ->
            val job = writer(channel = channel) { awaitCancellation() }
            close()
            closesDuringAttachment = closeCount
            job
        }

        try {
            assertFailsWith<IOException> {
                socket.attachForReading(ByteChannel())
            }
            assertEquals(0, closesDuringAttachment, "The I/O coroutine still owns the descriptor")
            socket.awaitClosed()
            assertEquals(1, socket.closeCount)
        } finally {
            socket.close()
            socket.socketContext.cancelAndJoin()
        }
    }

    @Test
    fun `close waits for a rejected channel to finish`() = runTest {
        var attachments = 0
        val cleanup = CompletableDeferred<Unit>()
        val socket = TestSocket { channel ->
            val rejected = attachments++ > 0
            writer(channel = channel) {
                try {
                    awaitCancellation()
                } finally {
                    if (rejected) withContext(NonCancellable) { cleanup.await() }
                }
            }
        }

        try {
            socket.attachForReading(ByteChannel())
            assertFailsWith<IllegalStateException> {
                socket.attachForReading(ByteChannel())
            }
            socket.close()
            assertEquals(0, socket.closeCount, "Rejected I/O must finish before the descriptor is closed")
            cleanup.complete(Unit)
            socket.awaitClosed()
            assertEquals(1, socket.closeCount)
        } finally {
            cleanup.complete(Unit)
            socket.close()
            socket.socketContext.cancelAndJoin()
        }
    }

    // Guard: passes without the fix too, and checks that tracking channels does not make close wait forever.
    @Test
    fun `failed channel creation does not block close`() = runTest {
        val socket = TestSocket { throw IOException("Channel creation failed") }
        try {
            assertFailsWith<IOException> {
                socket.attachForReading(ByteChannel())
            }
            socket.close()
            socket.awaitClosed()
            assertEquals(1, socket.closeCount)
        } finally {
            socket.close()
            socket.socketContext.cancelAndJoin()
        }
    }

    @Test
    fun `close waits for a descriptor call to return`() = runTest {
        val socket = TestSocket { error("No channel in this test") }

        val closesDuringCall = socket.useDescriptor {
            socket.close()
            socket.closeCount
        }

        assertEquals(0, closesDuringCall, "The descriptor call still owns the descriptor")
        socket.awaitClosed()
        assertEquals(1, socket.closeCount)
    }

    @Test
    fun `descriptor calls fail once the descriptor is closed`() = runTest {
        val socket = TestSocket { error("No channel in this test") }
        socket.close()
        socket.awaitClosed()

        assertFailsWith<IOException> {
            socket.useDescriptor { fail("The descriptor is already closed") }
        }
        assertFailsWith<IOException> {
            socket.attachForReading(ByteChannel())
        }
        assertEquals(1, socket.closeCount)
    }

    private class TestSocket(
        private val attach: TestSocket.(ByteChannel) -> WriterJob
    ) : SocketBase(EmptyCoroutineContext) {
        override val descriptor: Int = -1
        override val coroutineContext: CoroutineContext
            get() = super.coroutineContext + Dispatchers.Unconfined

        var closeCount: Int = 0
            private set

        override fun attachForReadingImpl(channel: ByteChannel): WriterJob = attach(channel)

        override fun attachForWritingImpl(channel: ByteChannel): ReaderJob = error("No write channel in this test")

        override fun actualClose(): Throwable? {
            closeCount++
            return null
        }
    }
}
