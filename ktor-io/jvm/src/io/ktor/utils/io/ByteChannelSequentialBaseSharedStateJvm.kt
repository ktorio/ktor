// ktlint-disable filename
/*
* Copyright 2014-2021 JetBrains s.r.o and contributors. Use of this source code is governed by the Apache 2.0 license.
*/

package io.ktor.utils.io

import io.ktor.utils.io.core.*
import io.ktor.utils.io.core.internal.*

internal actual class ByteChannelSequentialBaseSharedState actual constructor() {
    @Volatile
    actual var closed: Boolean = false

    @Volatile
    actual var readByteOrder: ByteOrder = ByteOrder.BIG_ENDIAN

    @Volatile
    actual var writeByteOrder: ByteOrder = ByteOrder.BIG_ENDIAN

    @Volatile
    actual var totalBytesRead: Long = 0L

    @Volatile
    actual var totalBytesWritten: Long = 0L

    @Volatile
    actual var closedCause: Throwable? = null

    @Volatile
    actual var lastReadAvailable: Int = 0

    @Volatile
    actual var lastReadView: ChunkBuffer = ChunkBuffer.Empty
}
