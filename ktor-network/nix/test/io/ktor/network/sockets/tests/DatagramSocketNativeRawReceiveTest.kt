/*
 * Copyright 2014-2026 JetBrains s.r.o and contributors.
 * Use of this source code is governed by the Apache 2.0 license.
 */

package io.ktor.network.sockets.tests

import io.ktor.network.selector.*
import io.ktor.network.sockets.*
import io.ktor.network.util.*
import io.ktor.utils.io.core.buildPacket
import io.ktor.utils.io.core.writeFully
import kotlinx.cinterop.ExperimentalForeignApi
import kotlinx.cinterop.addressOf
import kotlinx.cinterop.convert
import kotlinx.cinterop.usePinned
import kotlinx.coroutines.*
import platform.posix.sendto
import kotlin.ByteArray
import kotlin.IllegalArgumentException
import kotlin.IllegalStateException
import kotlin.Int
import kotlin.OptIn
import kotlin.Unit
import kotlin.byteArrayOf
import kotlin.experimental.ExperimentalNativeApi
import kotlin.native.ref.WeakReference
import kotlin.native.runtime.GC
import kotlin.native.runtime.NativeRuntimeApi
import kotlin.repeat
import kotlin.runCatching
import kotlin.test.*
import kotlin.time.Duration.Companion.milliseconds
import kotlin.to
import kotlin.use

class DatagramSocketNativeRawReceiveTest {

    @Test
    fun testReceiveIntoWithOffset() = testSockets { selector ->
        withCallerBufferSockets(selector) { receiver, sender ->
            val payload = byteArrayOf(10, 20, 30)
            val buffer = ByteArray(10) { -1 }
            val offset = 2
            val length = 5

            sendBytes(sender, receiver.localAddress, payload)

            val result = withTimeout(5_000.milliseconds) {
                receiver.receiveInto(buffer, offset, length)
            }

            assertEquals(payload.size, result.byteCount)
            assertEquals(sender.localAddress, result.senderAddress)

            val expected = ByteArray(10) { -1 }
            payload.copyInto(expected, destinationOffset = offset)

            assertContentEquals(expected, buffer)
        }
    }

    @Test
    fun testReceiveIntoAfterCancellation() = testSockets { selector ->
        withCallerBufferSockets(selector) { receiver, sender ->
            coroutineScope {
                val pendingReceive = launch(
                    start = CoroutineStart.UNDISPATCHED
                ) {
                    receiver.receiveInto(ByteArray(16), 0, 16)
                }

                pendingReceive.cancelAndJoin()
                assertTrue(pendingReceive.isCancelled)

                val payload = byteArrayOf(10, 20, 30)
                sendBytes(sender, receiver.localAddress, payload)

                val buffer = ByteArray(16)
                val result = withTimeout(5_000.milliseconds) {
                    receiver.receiveInto(buffer, 0, buffer.size)
                }

                assertEquals(payload.size, result.byteCount)
                assertEquals(sender.localAddress, result.senderAddress)
                assertContentEquals(
                    payload,
                    buffer.copyOfRange(0, result.byteCount)
                )
            }
        }
    }

    @Test
    fun testCloseWhileReceiving() = testSockets { selector ->
        withCallerBufferSockets(selector) { receiver, _ ->
            coroutineScope {
                val pending = async(
                    start = CoroutineStart.UNDISPATCHED
                ) {
                    runCatching {
                        receiver.receiveInto(ByteArray(16), 0, 16)
                    }
                }

                receiver.close()

                // The timeout is outside runCatching, so it cannot
                // accidentally count as the expected receive failure.
                val outcome = withTimeout(5_000.milliseconds) {
                    pending.await()
                }

                assertTrue(outcome.isFailure)
            }
        }
    }

    @Test
    fun testConcurrentReceives() = testSockets { selector ->
        withCallerBufferSockets(selector) { receiver, sender ->
            coroutineScope {
                val firstBuffer = ByteArray(16)
                val secondBuffer = ByteArray(16)

                val first = async(
                    start = CoroutineStart.UNDISPATCHED
                ) {
                    receiver.receiveInto(
                        firstBuffer,
                        0,
                        firstBuffer.size
                    )
                }

                val second = async(
                    start = CoroutineStart.UNDISPATCHED
                ) {
                    receiver.receiveInto(
                        secondBuffer,
                        0,
                        secondBuffer.size
                    )
                }

                val firstPayload = byteArrayOf(10, 20)
                val secondPayload = byteArrayOf(30, 40, 50)

                sendBytes(sender, receiver.localAddress, firstPayload)
                sendBytes(sender, receiver.localAddress, secondPayload)

                withTimeout(5_000.milliseconds) {
                    val firstResult = first.await()
                    val secondResult = second.await()

                    assertEquals(
                        firstPayload.size,
                        firstResult.byteCount
                    )
                    assertEquals(
                        secondPayload.size,
                        secondResult.byteCount
                    )
                    assertEquals(
                        sender.localAddress,
                        firstResult.senderAddress
                    )
                    assertEquals(
                        sender.localAddress,
                        secondResult.senderAddress
                    )
                    assertContentEquals(
                        firstPayload,
                        firstBuffer.copyOfRange(
                            0,
                            firstResult.byteCount
                        )
                    )
                    assertContentEquals(
                        secondPayload,
                        secondBuffer.copyOfRange(
                            0,
                            secondResult.byteCount
                        )
                    )
                }
            }
        }
    }

    @Test
    fun testCancelReceiveWaitingForMutex() = testSockets { selector ->
        withCallerBufferSockets(selector) { receiver, sender ->
            coroutineScope {
                val firstBuffer = ByteArray(16)
                val cancelledBuffer = ByteArray(16) { -1 }

                val first = async(
                    start = CoroutineStart.UNDISPATCHED
                ) {
                    receiver.receiveInto(
                        firstBuffer,
                        0,
                        firstBuffer.size
                    )
                }

                val waiting = launch(
                    start = CoroutineStart.UNDISPATCHED
                ) {
                    receiver.receiveInto(
                        cancelledBuffer,
                        0,
                        cancelledBuffer.size
                    )
                }

                waiting.cancelAndJoin()
                assertTrue(waiting.isCancelled)

                val payload = byteArrayOf(10, 20, 30)
                sendBytes(sender, receiver.localAddress, payload)

                val firstResult = withTimeout(5_000.milliseconds) {
                    first.await()
                }

                assertEquals(payload.size, firstResult.byteCount)
                assertContentEquals(
                    payload,
                    firstBuffer.copyOfRange(0, firstResult.byteCount)
                )
                assertContentEquals(
                    ByteArray(16) { -1 },
                    cancelledBuffer
                )

                val nextBuffer = ByteArray(16)
                sendBytes(sender, receiver.localAddress, payload)

                val nextResult = withTimeout(5_000.milliseconds) {
                    receiver.receiveInto(
                        nextBuffer,
                        0,
                        nextBuffer.size
                    )
                }

                assertEquals(payload.size, nextResult.byteCount)
                assertContentEquals(
                    payload,
                    nextBuffer.copyOfRange(0, nextResult.byteCount)
                )
            }
        }
    }

    @Test
    fun testInvalidReceiveRanges() = testSockets { selector ->
        withCallerBufferSockets(selector) { receiver, sender ->
            val buffer = ByteArray(8) { -1 }
            val invalidRanges = listOf(
                -1 to 1,
                8 to 1,
                Int.MAX_VALUE to 1,
                0 to -1,
                0 to 0,
                0 to 9,
                7 to 2,
                1 to Int.MAX_VALUE
            )

            for ((offset, length) in invalidRanges) {
                assertFailsWith<IllegalArgumentException>(
                    "offset=$offset, length=$length"
                ) {
                    receiver.receiveInto(buffer, offset, length)
                }
            }

            assertFailsWith<IllegalArgumentException> {
                receiver.receiveInto(ByteArray(0), 0, 0)
            }

            assertContentEquals(ByteArray(8) { -1 }, buffer)

            // Invalid calls must leave the socket usable.
            sendBytes(sender, receiver.localAddress, byteArrayOf(42))

            val result = withTimeout(5_000.milliseconds) {
                receiver.receiveInto(buffer, offset = 7, length = 1)
            }

            assertEquals(1, result.byteCount)
            assertContentEquals(
                byteArrayOf(-1, -1, -1, -1, -1, -1, -1, 42),
                buffer
            )
        }
    }

    @Test
    fun testChannelModeRejectsReceiveInto() = testSockets { selector ->
        aSocket(selector).udp().bind("127.0.0.1", 0).use { receiver ->
            assertFailsWith<IllegalStateException> {
                (receiver as DatagramSocketNative)
                    .receiveInto(ByteArray(16), 0, 16)
            }
        }
    }

    @Test
    fun testCallerBufferModeRejectsIncoming() = testSockets { selector ->
        withCallerBufferSockets(selector) { receiver, _ ->
            assertFailsWith<IllegalStateException> {
                receiver.incoming
            }
        }
    }

    @Test
    fun testCopyPreservesReceiveMode() {
        val original = SocketOptions.create().peer().udp()

        assertEquals(DatagramReceiveMode.CHANNEL, original.receiveMode)

        original.receiveMode = DatagramReceiveMode.CALLER_BUFFER

        val copied = original.copy()
        val converted = original.udp()

        assertEquals(
            DatagramReceiveMode.CALLER_BUFFER,
            copied.receiveMode,
            "copy() lost receiveMode"
        )
        assertEquals(
            DatagramReceiveMode.CALLER_BUFFER,
            converted.receiveMode,
            "udp() lost receiveMode"
        )

        copied.receiveMode = DatagramReceiveMode.CHANNEL

        assertEquals(
            DatagramReceiveMode.CALLER_BUFFER,
            original.receiveMode,
            "Changing the copy changed the original"
        )
    }

    @Test
    fun testConnectedSocketReceiveInto() = testSockets { selector ->
        aSocket(selector).udp().bind("127.0.0.1", 0).use { sender ->
            aSocket(selector).udp().connect(
                remoteAddress = sender.localAddress,
                localAddress = InetSocketAddress("127.0.0.1", 0)
            ) {
                receiveMode = DatagramReceiveMode.CALLER_BUFFER
            }.use { receiver ->
                val payload = byteArrayOf(10, 20, 30)
                sendBytes(sender, receiver.localAddress, payload)

                val buffer = ByteArray(16)
                val result = withTimeout(5_000.milliseconds) {
                    (receiver as DatagramSocketNative)
                        .receiveInto(buffer, 0, buffer.size)
                }

                assertEquals(payload.size, result.byteCount)
                assertEquals(sender.localAddress, result.senderAddress)
                assertContentEquals(
                    payload,
                    buffer.copyOfRange(0, result.byteCount)
                )
            }
        }
    }

    @Test
    fun testReceiveIntoTruncatesDatagram() = testSockets { selector ->
        withCallerBufferSockets(selector) { receiver, sender ->
            sendBytes(
                sender,
                receiver.localAddress,
                byteArrayOf(10, 20, 30, 40, 50)
            )

            val buffer = ByteArray(6) { -1 }
            val result = withTimeout(5_000.milliseconds) {
                receiver.receiveInto(buffer, offset = 2, length = 2)
            }

            assertEquals(2, result.byteCount)
            assertEquals(sender.localAddress, result.senderAddress)
            assertContentEquals(
                byteArrayOf(-1, -1, 10, 20, -1, -1),
                buffer
            )

            // The next receive must get the next datagram,
            // rather than the remainder of the previous one.
            sendBytes(sender, receiver.localAddress, byteArrayOf(99))

            val nextBuffer = ByteArray(8)
            val nextResult = withTimeout(5_000.milliseconds) {
                receiver.receiveInto(nextBuffer, 0, nextBuffer.size)
            }

            assertEquals(1, nextResult.byteCount)
            assertEquals(99.toByte(), nextBuffer[0])
        }
    }

    @OptIn(ExperimentalForeignApi::class)
    @Test
    fun testReceiveIntoEmptyDatagram() = testSockets { selector ->
        withCallerBufferSockets(selector) { receiver, sender ->
            val descriptor = (sender as DatagramSocketNative).descriptor
            val payload = byteArrayOf(42)

            fun rawSend(length: Int) {
                payload.usePinned { pinned ->
                    receiver.localAddress.address.nativeAddress { address, addressSize ->
                        val sent = sendto(
                            descriptor,
                            pinned.addressOf(0),
                            length.convert(),
                            0,
                            address,
                            addressSize.convert()
                        )

                        assertEquals(
                            length.toLong(),
                            sent.toLong(),
                            "raw sendto failed"
                        )
                    }
                }
            }

            rawSend(0)

            val buffer = ByteArray(8) { -1 }
            val result = withTimeout(5_000.milliseconds) {
                receiver.receiveInto(buffer, 0, buffer.size)
            }

            assertEquals(0, result.byteCount)
            assertEquals(sender.localAddress, result.senderAddress)
            assertContentEquals(ByteArray(8) { -1 }, buffer)

            rawSend(payload.size)

            val nextResult = withTimeout(5_000.milliseconds) {
                receiver.receiveInto(buffer, 0, buffer.size)
            }

            assertEquals(1, nextResult.byteCount)
            assertEquals(sender.localAddress, nextResult.senderAddress)
            assertEquals(42.toByte(), buffer[0])
        }
    }

    @OptIn(ExperimentalNativeApi::class, NativeRuntimeApi::class)
    @Test
    fun testCancelledReceivesReleaseBuffersWithoutTraffic() = testSockets { selector ->
        withCallerBufferSockets(selector) { receiver, _ ->
            val references = mutableListOf<WeakReference<ByteArray>>()

            repeat(20) {
                references += cancelReceiveAndTrackBuffer(receiver)
            }

            var retained = references.size

            withTimeoutOrNull(5_000.milliseconds) {
                do {
                    // Allow the selector to process cancellation notifications.
                    delay(10.milliseconds)

                    GC.collect()
                    retained = references.count { it.value != null }
                } while (retained != 0)
            }

            assertEquals(
                0,
                retained,
                "Cancelled receives retained $retained buffers without traffic"
            )
        }
    }

    @OptIn(ExperimentalNativeApi::class)
    private suspend fun cancelReceiveAndTrackBuffer(
        receiver: DatagramSocketNative
    ): WeakReference<ByteArray> = coroutineScope {
        val buffer = ByteArray(1024)
        val reference = WeakReference(buffer)

        val pending = launch(start = CoroutineStart.UNDISPATCHED) {
            receiver.receiveInto(buffer, 0, buffer.size)
        }

        pending.cancelAndJoin()
        reference
    }

    private suspend fun withCallerBufferSockets(
        selector: SelectorManager,
        block: suspend (
            DatagramSocketNative,
            BoundDatagramSocket
        ) -> Unit
    ) {
        aSocket(selector).udp().bind("127.0.0.1", 0) {
            receiveMode = DatagramReceiveMode.CALLER_BUFFER
        }.use { receiver ->
            aSocket(selector).udp().bind("127.0.0.1", 0).use { sender ->
                block(receiver as DatagramSocketNative, sender)
            }
        }
    }

    private suspend fun sendBytes(
        sender: BoundDatagramSocket,
        destination: SocketAddress,
        payload: ByteArray
    ) {
        sender.outgoing.send(
            Datagram(
                buildPacket { writeFully(payload) },
                destination
            )
        )
    }
}
