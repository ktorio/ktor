package io.ktor.network.tls

import io.ktor.http.cio.internals.*
import kotlinx.coroutines.experimental.io.packet.*
import kotlinx.io.core.*
import kotlinx.io.core.ByteReadPacket
import java.io.*
import java.security.*

internal fun ByteReadPacket.duplicate(): Pair<ByteReadPacket, ByteReadPacket> {
    if (this.isEmpty) return ByteReadPacket.Empty to ByteReadPacket.Empty
    return this to copy()
}

internal class Digest : Closeable {
    private val state = WritePacket()

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
        writeTLSHandshakeType(record.type, record.packet.remaining)
        if (record.packet.remaining > 0) writePacket(record.packet.copy())
    })
}
