/*
 * Copyright 2014-2024 JetBrains s.r.o and contributors. Use of this source code is governed by the Apache 2.0 license.
 */

package io.ktor.network.tls

import io.ktor.network.tls.extensions.*
import io.ktor.utils.io.core.*

internal val readTLSExtensions = BytePacketParser { input ->

    if (input.remaining.toInt() == 0)
        emptyList()

    else buildList {
        // handle extensions
        val extensionSize = input.readShort().toInt() and 0xffff

        if (input.remaining.toInt() != extensionSize) {
            throw TLSException("Invalid extensions size: requested $extensionSize, available ${input.remaining}")
        }

        while (input.remaining > 0) {
            val type = input.readShort().toInt() and 0xffff
            val length = input.readShort().toInt() and 0xffff

            add(TLSExtension(
                TLSExtensionType.byCode(type),
                buildPacket { writeFully(input.readBytes(length)) }
            ))
        }
    }
}


private const val MAX_SERVER_NAME_LENGTH: Int = Short.MAX_VALUE - 5
internal fun buildServerNameExtension(name: String): TLSExtension = TLSExtension(
    type = TLSExtensionType.SERVER_NAME,
    packet = buildPacket {
        require(name.length < MAX_SERVER_NAME_LENGTH) {
            "Server name length limit exceeded: at most $MAX_SERVER_NAME_LENGTH characters allowed"
        }

        writeShort(TLSExtensionType.SERVER_NAME.code) // server_name
        writeShort((name.length + 2 + 1 + 2).toShort()) // length
        writeShort((name.length + 2 + 1).toShort()) // list length
        writeByte(0) // type: host_name
        writeShort(name.length.toShort()) // name length
        writeText(name)
    }
)

private const val MAX_CURVES_QUANTITY: Int = Short.MAX_VALUE / 2 - 1

internal fun buildECCurvesExtension(curves: List<NamedCurve> = SupportedNamedCurves): TLSExtension =
    TLSExtension(
        type = TLSExtensionType.ELLIPTIC_CURVES,
        packet = buildPacket {
            require(curves.size <= MAX_CURVES_QUANTITY) {
                "Too many named curves provided: at most $MAX_CURVES_QUANTITY could be provided"
            }

            writeShort(TLSExtensionType.ELLIPTIC_CURVES.code)
            val size = curves.size * 2

            writeShort((2 + size).toShort()) // extension length
            writeShort(size.toShort()) // list length

            curves.forEach {
                writeShort(it.code)
            }
        }
    )

internal fun buildECPointFormatExtension(
    formats: List<PointFormat> = SupportedPointFormats
): TLSExtension =
    TLSExtension(
        type = TLSExtensionType.EC_POINT_FORMAT,
        packet = buildPacket {
            writeShort(TLSExtensionType.EC_POINT_FORMAT.code)

            val size = formats.size
            writeShort((1 + size).toShort()) // extension length

            writeByte(size.toByte()) // list length
            formats.forEach {
                writeByte(it.code)
            }
        }
    )
