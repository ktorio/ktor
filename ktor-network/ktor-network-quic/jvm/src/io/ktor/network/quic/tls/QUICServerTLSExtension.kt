/*
 * Copyright 2014-2023 JetBrains s.r.o and contributors. Use of this source code is governed by the Apache 2.0 license.
 */

package io.ktor.network.quic.tls

import io.ktor.network.quic.connections.*
import io.ktor.utils.io.core.*
import net.luminis.tls.extension.*

internal class QUICServerTLSExtension(
    val transportParameters: TransportParameters,
    private val isServer: Boolean,
) : Extension() {
    override fun getBytes(): ByteArray = buildPacket {
        val params = transportParameters.toBytes(isServer)
        writeShort(EXTENSION_TYPE)
        writeShort(params.size.toShort())
        writeFully(params)
    }.readBytes()

    companion object {
        private const val EXTENSION_TYPE: Short = 0x39

        fun fromBytes(bytes: ByteReadPacket, isServer: Boolean): QUICServerTLSExtension {
            val extensionType = bytes.readShort()
            if (extensionType != EXTENSION_TYPE) {
                error("") // todo raise error
            }

            val length = bytes.readShort().toInt()
            return QUICServerTLSExtension(TransportParameters.fromBytes(bytes, length, isServer), isServer)
        }
    }
}
