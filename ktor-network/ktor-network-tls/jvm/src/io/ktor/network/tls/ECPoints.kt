/*
 * Copyright 2014-2024 JetBrains s.r.o and contributors. Use of this source code is governed by the Apache 2.0 license.
 */

package io.ktor.network.tls

import io.ktor.utils.io.core.*
import java.math.*
import java.security.spec.*

internal suspend fun readECPoint(fieldSize: Int, packet: ByteReadPacket): ECPoint =
    ECPointReader(fieldSize).read(packet)

private class ECPointReader(val fieldSize: Int): BytePacketParser<ECPoint> {
    override suspend fun read(input: ByteReadPacket): ECPoint {
        val pointSize = input.readByte().toInt() and 0xff

        val tag = input.readByte()
        if (tag != 4.toByte()) throw TLSException("Point should be uncompressed")

        val componentLength = (pointSize - 1) / 2
        if ((fieldSize + 7) ushr 3 != componentLength) throw TLSException("Invalid point component length")

        return ECPoint(
            BigInteger(1, input.readBytes(componentLength)),
            BigInteger(1, input.readBytes(componentLength))
        )
    }
}

internal fun BytePacketBuilder.writeECPoint(point: ECPoint, fieldSize: Int) {
    val pointData = buildPacket {
        writeByte(4) // 4 - uncompressed
        writeAligned(point.affineX.toByteArray(), fieldSize)
        writeAligned(point.affineY.toByteArray(), fieldSize)
    }

    writeByte(pointData.remaining.toByte())
    writePacket(pointData)
}

private fun BytePacketBuilder.writeAligned(src: ByteArray, fieldSize: Int) {
    val expectedSize = (fieldSize + 7) ushr 3
    val index = src.indexOfFirst { it != 0.toByte() }
    val padding = expectedSize - (src.size - index)

    if (padding > 0) writeFully(ByteArray(padding))
    writeFully(src, index, src.size - index)
}
