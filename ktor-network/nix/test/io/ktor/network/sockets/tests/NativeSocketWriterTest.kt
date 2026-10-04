/*
 * Copyright 2014-2026 JetBrains s.r.o and contributors. Use of this source code is governed by the Apache 2.0 license.
 */

package io.ktor.network.sockets.tests

import io.ktor.network.selector.*
import io.ktor.network.sockets.*
import io.ktor.utils.io.*
import kotlinx.cinterop.*
import kotlinx.coroutines.*
import platform.posix.*
import kotlin.coroutines.*
import kotlin.test.*

@OptIn(ExperimentalForeignApi::class)
class NativeSocketWriterTest {
    @Test
    fun `completed writer has shut down the socket output`() = runBlocking {
        repeat(1_000) {
            withSocketPair { descriptor, peer ->
                val channel = ByteChannel().apply { close() }
                val writer = attachForWritingImpl(channel, descriptor, selectable(descriptor), unusedSelector)
                while (!writer.isCompleted) yield()
                assertOutputClosed(peer)
                writer.join()
            }
        }
    }

    @Test
    fun `writer cancelled before starting shuts down the socket output`() = runBlocking {
        withSocketPair { descriptor, peer ->
            val scope = CoroutineScope(Job().apply { cancel() })
            val writer = scope.attachForWritingImpl(ByteChannel(), descriptor, selectable(descriptor), unusedSelector)
            withTimeout(5_000) {
                while (!isOutputClosed(peer)) yield()
            }
            writer.join()
            assertTrue(writer.isCompleted)
            assertOutputClosed(peer)
        }
    }

    private fun assertOutputClosed(peer: Int) = memScoped {
        val byte = alloc<ByteVar>()
        assertEquals(
            0L,
            recv(peer, byte.ptr, 1u, MSG_DONTWAIT).toLong(),
            "Writer completed before SHUT_WR: errno=$errno"
        )
    }

    private fun isOutputClosed(peer: Int): Boolean = memScoped {
        val byte = alloc<ByteVar>()
        recv(peer, byte.ptr, 1u, MSG_DONTWAIT).toLong() == 0L
    }

    private inline fun withSocketPair(block: (Int, Int) -> Unit) = memScoped {
        val descriptors = allocArray<IntVar>(2)
        assertEquals(0, socketpair(AF_UNIX, SOCK_STREAM, 0, descriptors))
        try {
            block(descriptors[0], descriptors[1])
        } finally {
            close(descriptors[0])
            close(descriptors[1])
        }
    }

    private fun selectable(fd: Int) = object : Selectable {
        override val descriptor: Int = fd
    }

    private val unusedSelector = object : SelectorManager {
        override val coroutineContext: CoroutineContext = EmptyCoroutineContext
        override fun notifyClosed(selectable: Selectable) = error("Unexpected descriptor close")
        override suspend fun select(selectable: Selectable, interest: SelectInterest) = error("Unexpected selection")
        override fun close() = Unit
    }
}
