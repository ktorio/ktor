/*
 * Copyright 2014-2023 JetBrains s.r.o and contributors. Use of this source code is governed by the Apache 2.0 license.
 */

package io.ktor.network.quic.bytes

import io.ktor.utils.io.core.*

/**
 * Reads an unsigned byte from a [ByteReadPacket]. Returns null if EOF
 */
@ExperimentalUnsignedTypes
internal inline fun ByteReadPacket.readUByteOrElse(elseBlock: () -> UByte): UByte {
    if (isEmpty) {
        elseBlock()
    }
    return readUByte()
}
