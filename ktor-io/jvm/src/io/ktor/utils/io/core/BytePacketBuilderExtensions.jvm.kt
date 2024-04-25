/*
 * Copyright 2014-2024 JetBrains s.r.o and contributors. Use of this source code is governed by the Apache 2.0 license.
 */

package io.ktor.utils.io.core

import kotlinx.io.*
import java.nio.*

@Suppress("DEPRECATION")
@OptIn(InternalIoApi::class)
public fun BytePacketBuilder.writeFully(buffer: ByteBuffer) {
    this.buffer.transferFrom(buffer)
}
