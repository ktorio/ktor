/*
 * Copyright 2014-2019 JetBrains s.r.o and contributors. Use of this source code is governed by the Apache 2.0 license.
 */

package io.ktor.network.tls

import io.ktor.http.cio.internals.*
import io.ktor.utils.io.core.*
import java.security.*

internal fun Digest(): Digest = Digest(BytePacketBuilder())

internal inline class Digest(val state: BytePacketBuilder) : Closeable {

    fun update(packet: ByteReadPacket) = synchronized(this) {
        if (packet.isEmpty) return
        state.writePacket(packet.copy())
    }

    fun doHash(hashName: String): ByteArray = synchronized(this) {
        state.preview { handshakes: ByteReadPacket ->
            val digest = MessageDigest.getInstance(hashName)!!

            val buffer = DefaultByteBufferPool.borrow()
            try {
                while (!handshakes.isEmpty) {
                    val rc = handshakes.readAvailable(buffer)
                    if (rc == -1) break
                    buffer.flip()
                    digest.update(buffer)
                    buffer.clear()
                }

                return@preview digest.digest()
            } finally {
                DefaultByteBufferPool.recycle(buffer)
            }
        }
    }

    override fun close() {
        state.release()
    }

}

internal operator fun Digest.plusAssign(record: TLSHandshake) {
    check(record.type != TLSHandshakeType.HelloRequest)

    update(buildPacket {
        writeTLSHandshakeType(record.type, record.packet.remaining.toInt())
        if (record.packet.remaining > 0) writePacket(record.packet.copy())
    })
}
