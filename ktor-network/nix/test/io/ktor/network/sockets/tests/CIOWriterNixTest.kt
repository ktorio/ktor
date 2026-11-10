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
import kotlin.test.*

@OptIn(ExperimentalForeignApi::class)
class CIOWriterNixTest {

    @Test
    fun `output is shut down before writer completes`() = testSockets { selector ->
        repeat(ITERATIONS) { iteration ->
            val (writeEnd, peer) = socketPair()
            try {
                val channel = ByteChannel()
                val job = attachForWritingImpl(channel, writeEnd, RawSelectable(writeEnd), selector)
                while (job.job.children.none()) {
                    yield()
                }
                val bodyFinished = job.job.children.singleOrNull()
                    ?: fail("the reader builder is expected to run the body under a single nested job")
                val observation = async(Dispatchers.Unconfined) {
                    bodyFinished.join()
                    // Resumed inline on the writer thread. The outer job must not have completed yet, otherwise this
                    // check would run after its completion handlers and prove nothing.
                    assertFalse(job.isCompleted, "iteration $iteration: observed after the writer job completed")
                    receive(peer) to receive(peer)
                }

                channel.writeByte(PAYLOAD)
                channel.flushAndClose()

                val (payload, eof) = observation.await()
                assertEquals(1, payload.first, "iteration $iteration: the payload was not delivered")
                assertEquals(
                    0,
                    eof.first,
                    "iteration $iteration: the writer body has finished " +
                        "but the peer does not see EOF (errno ${eof.second})"
                )
                job.join()
            } finally {
                close(writeEnd)
                close(peer)
            }
        }
    }

    private class RawSelectable(override val descriptor: Int) : Selectable

    private fun socketPair(): Pair<Int, Int> = memScoped {
        val descriptors = allocArray<IntVar>(2)
        check(socketpair(AF_UNIX, SOCK_STREAM, 0, descriptors) == 0) { "socketpair failed: errno $errno" }
        for (index in 0..1) {
            val descriptor = descriptors[index]
            check(fcntl(descriptor, F_SETFL, fcntl(descriptor, F_GETFL) or O_NONBLOCK) == 0) {
                "fcntl failed: errno $errno"
            }
        }
        descriptors[0] to descriptors[1]
    }

    /** Non-blocking receive: returns the byte count (0 on EOF, -1 on error) together with errno. */
    private fun receive(descriptor: Int): Pair<Int, Int> {
        val buffer = ByteArray(16)
        val count = buffer.usePinned { pinned ->
            recv(descriptor, pinned.addressOf(0), buffer.size.convert(), 0).toInt()
        }
        return count to (if (count < 0) errno else 0)
    }

    private companion object {
        const val ITERATIONS = 10
        const val PAYLOAD: Byte = 42
    }
}
