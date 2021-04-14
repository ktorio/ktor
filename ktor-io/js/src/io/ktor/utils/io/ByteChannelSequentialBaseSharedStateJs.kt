// ktlint-disable filename
/*
 * Copyright 2014-2020 JetBrains s.r.o and contributors. Use of this source code is governed by the Apache 2.0 license.
 */

package io.ktor.utils.io

import io.ktor.utils.io.core.internal.*

internal actual class ByteChannelSequentialBaseSharedState actual constructor() {
    actual var closed: Boolean = false

    actual var totalBytesRead: Long = 0L

    actual var totalBytesWritten: Long = 0L

    actual var closedCause: Throwable? = null

    actual var lastReadAvailable: Int = 0

    actual var lastReadView: ChunkBuffer = ChunkBuffer.Empty
}
