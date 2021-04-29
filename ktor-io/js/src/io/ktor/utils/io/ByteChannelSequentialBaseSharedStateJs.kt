// ktlint-disable filename
/*
* Copyright 2014-2021 JetBrains s.r.o and contributors. Use of this source code is governed by the Apache 2.0 license.
*/

package io.ktor.utils.io

import io.ktor.utils.io.core.*
import io.ktor.utils.io.core.internal.*
import kotlin.jvm.*

internal actual class ByteChannelSequentialBaseSharedState actual constructor() {
    actual var closed: Boolean = false

    actual var readByteOrder: ByteOrder = ByteOrder.BIG_ENDIAN

    actual var writeByteOrder: ByteOrder = ByteOrder.BIG_ENDIAN

    actual var totalBytesRead: Long = 0L

    actual var totalBytesWritten: Long = 0L

    actual var closedCause: Throwable? = null

    actual var lastReadAvailable: Int = 0

    actual var lastReadView: ChunkBuffer = ChunkBuffer.Empty
}
