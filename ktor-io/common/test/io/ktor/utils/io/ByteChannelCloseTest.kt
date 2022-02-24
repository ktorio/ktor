/*
 * Copyright 2014-2021 JetBrains s.r.o and contributors. Use of this source code is governed by the Apache 2.0 license.
 */

package io.ktor.utils.io

import kotlin.reflect.*
import kotlin.test.*

class ReadPacketWithoutExceptionByteChannelCloseTest : ByteChannelCloseTest(
    ExpectedFailureException::class,
    { close(ExpectedFailureException()) },
    { readPacket(Int.MAX_VALUE) }
)

class ReadRemainingWithExceptionByteChannelCloseTest : ByteChannelCloseTest(
    ExpectedFailureException::class,
    { close(ExpectedFailureException()) },
    { readRemaining() }
)

class ReadRemainingWithoutExceptionByteChannelCloseTest : ByteChannelCloseTest(
    null,
    { close() },
    { readRemaining() }
)

abstract class ByteChannelCloseTest(
    private val expectedExceptionClass: KClass<out Throwable>?,
    private val closeChannel: ByteChannel.() -> Unit,
    private val readFromChannel: suspend ByteChannel.() -> Unit
) : ByteChannelTestBase() {

    private inline fun assertMayFail(block: () -> Unit) {
        when (expectedExceptionClass) {
            null -> block()
            else -> assertFailsWith(expectedExceptionClass, block)
        }
    }

    @Test
    fun testImmediateFailurePropagation() = runTest {
        ch.closeChannel()
        assertMayFail {
            ch.readFromChannel()
        }
    }

    @Test
    fun testFailureAfterSuspend() = runTest {
        launch {
            expect(1)
            assertMayFail {
                ch.readFromChannel()
            }
            expect(3)
        }

        yield()

        expect(2)

        ch.closeChannel()
    }

    @Test
    fun testFailureAfterFewBytes() = runTest {
        launch {
            expect(1)
            assertMayFail {
                ch.readFromChannel()
            }
            expect(4)
        }

        yield()

        expect(2)

        ch.writeFully(byteArrayOf(1, 2, 3))
        ch.flush()

        yield()

        expect(3)

        ch.closeChannel()
    }

    @Test
    fun testFailureAfterFewBytesNonFlushed() = runTest {
        launch {
            expect(1)
            assertMayFail {
                ch.readFromChannel()
            }
            expect(4)
        }

        yield()

        expect(2)

        ch.writeFully(byteArrayOf(1, 2, 3))

        yield()

        expect(3)

        ch.closeChannel()
    }

    class ExpectedFailureException : Exception()
}
