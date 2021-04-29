/*
 * Copyright 2014-2021 JetBrains s.r.o and contributors. Use of this source code is governed by the Apache 2.0 license.
 */

package io.ktor.utils.io

import io.ktor.utils.io.core.*
import kotlinx.coroutines.channels.*
import io.ktor.utils.io.core.internal.*

class JvmReadPacketWithExceptionByteChannelCloseTest : ByteChannelCloseTest(
    ClosedReceiveChannelException::class,
    { close() },
    { readPacket(Int.MAX_VALUE) }
)

class JvmSequentialReadPacketWithExceptionByteChannelCloseTest : ByteChannelCloseTest(
    EOFException::class,
    { close() },
    { readPacket(Int.MAX_VALUE) }
) {
    override fun ByteChannel(autoFlush: Boolean): ByteChannel {
        return ByteChannelSequentialJVM(ChunkBuffer.Empty, autoFlush)
    }
}

class JvmReadFullyWithExceptionByteChannelCloseTest : ByteChannelCloseTest(
    ClosedReceiveChannelException::class,
    { close() },
    { readFully(ByteArray(10)) }
)

class JvmSequentialReadFullyWithExceptionByteChannelCloseTest : ByteChannelCloseTest(
    EOFException::class,
    { close() },
    { readFully(ByteArray(10)) }
) {
    override fun ByteChannel(autoFlush: Boolean): ByteChannel {
        return ByteChannelSequentialJVM(ChunkBuffer.Empty, autoFlush)
    }
}
