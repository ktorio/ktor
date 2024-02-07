/*
 * Copyright 2014-2024 JetBrains s.r.o and contributors. Use of this source code is governed by the Apache 2.0 license.
 */

package io.ktor.network.tls

import io.ktor.network.tls.extensions.*
import io.ktor.utils.io.core.*

internal val readTLSExtensions = BytePacketReader { input ->

    if (input.remaining.toInt() == 0) {
        emptyList()
    } else {
        buildList {
            // handle extensions
            val extensionSize = input.readShort().toInt() and 0xffff

            if (input.remaining.toInt() != extensionSize) {
                throw TLSValidationException(
                    "Invalid extensions size: requested $extensionSize, available ${input.remaining}"
                )
            }

            while (input.remaining > 0) {
                val type = input.readShort().toInt() and 0xffff
                val length = input.readShort().toInt() and 0xffff

                add(
                    TLSExtension(
                        TLSExtensionType.byCode(type),
                        buildPacket { writeFully(input.readBytes(length)) }
                    )
                )
            }
        }
    }
}

internal val writeTLSExtensions = BytePacketWriter<List<TLSExtension>> { output, extensions ->
    output.writeShort(extensions.sumOf {
        Short.SIZE_BYTES + // type short
            Short.SIZE_BYTES + // packet length short
            it.packet.remaining // actual packet length
    }.toShort())

    for (extension in extensions) {
        output.writeShort(extension.type.code)
        output.writeShort(extension.packet.remaining.toShort())
        output.writePacket(extension.packet)
    }
}

internal fun buildSignatureAlgorithmsExtension(
    algorithms: List<HashAndSign> = SupportedSignatureAlgorithms
) = TLSExtension(
    type = TLSExtensionType.SIGNATURE_ALGORITHMS,
    packet = buildPacket {
        writeShort((algorithms.size * 2).toShort()) // algorithms length in bytes
        algorithms.forEach {
            writeByte(it.hash.code)
            writeByte(it.sign.code)
        }
    }
)

private const val MAX_SERVER_NAME_LENGTH: Int = Short.MAX_VALUE - 5
internal fun buildServerNameExtension(name: String): TLSExtension = TLSExtension(
    type = TLSExtensionType.SERVER_NAME,
    packet = buildPacket {
        require(name.length < MAX_SERVER_NAME_LENGTH) {
            "Server name length limit exceeded: at most $MAX_SERVER_NAME_LENGTH characters allowed"
        }
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
            val size = curves.size * 2
            writeShort(size.toShort()) // list length in bytes
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
            writeByte(formats.size.toByte()) // list length
            formats.forEach {
                writeByte(it.code)
            }
        }
    )
