/*
 * Copyright 2014-2019 JetBrains s.r.o and contributors. Use of this source code is governed by the Apache 2.0 license.
 */

package io.ktor.network.tls

import io.ktor.utils.io.*
import io.ktor.utils.io.core.*

private const val MAX_TLS_FRAME_SIZE = 0x4800

internal suspend fun ByteReadChannel.readTLSRecord(): TLSRecord {
    val type = TLSRecordType.byCode(readByte().toInt() and 0xff)
    val version = readTLSVersion()

    val length = readShortCompatible() and 0xffff
    if (length > MAX_TLS_FRAME_SIZE) throw TLSException("Illegal TLS frame size: $length")

    val packet = readPacket(length)
    return TLSRecord(type, version, packet)
}

private suspend fun ByteReadChannel.readTLSVersion() =
    TLSVersion.byCode(readShortCompatible() and 0xffff)

internal fun ByteReadPacket.readTripleByteLength(): Int =
    (readByte().toInt() and 0xff shl 16) or (readShort().toInt() and 0xffff)

internal suspend fun ByteReadChannel.readShortCompatible(): Int {
    val first = readByte().toInt() and 0xff
    val second = readByte().toInt() and 0xff

    return (first shl 8) + second
}
