/*
 * Copyright 2014-2023 JetBrains s.r.o and contributors. Use of this source code is governed by the Apache 2.0 license.
 */

package io.ktor.network.quic.bytes

import io.ktor.utils.io.core.*

/**
 * Reads a byte from a [ByteReadPacket]. Returns null if EOF
 */
internal fun ByteReadPacket.readByteOrNull() = if (isNotEmpty) readByte() else null
