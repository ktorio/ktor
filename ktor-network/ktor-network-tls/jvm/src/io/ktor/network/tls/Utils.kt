/*
 * Copyright 2014-2019 JetBrains s.r.o and contributors. Use of this source code is governed by the Apache 2.0 license.
 */

package io.ktor.network.tls

import io.ktor.utils.io.core.*
import java.security.*

internal fun Digest(): Digest = Digest(BytePacketBuilder())

@JvmInline
internal value class Digest(val state: BytePacketBuilder) : Closeable {

    fun update(packet: ByteReadPacket) = synchronized(state) {
        if (packet.isEmpty) return
        state.writePacket(packet)
    }

    fun doHash(hashName: String, trimEnd: Int = 0): ByteArray = synchronized(state) {
        state.preview { handshakes: ByteReadPacket ->
            val length = handshakes.remaining.toInt() - trimEnd

            val digest = MessageDigest.getInstance(hashName)!!
            digest.update(handshakes.readByteBuffer(length))
            return@preview digest.digest()
        }
    }

    override fun close() {
        state.release()
    }
}
