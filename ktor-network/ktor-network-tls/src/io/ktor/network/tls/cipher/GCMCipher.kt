package io.ktor.network.tls.cipher

import io.ktor.network.tls.*

internal class GCMCipher(
    private val suite: CipherSuite,
    private val key: ByteArray
) : TLSCipher {
    private var inputCounter: Long = 0L
    private var outputCounter: Long = 0L

    override fun encrypt(record: TLSRecord): TLSRecord {
        val cipher = encryptCipher(

            suite, key, record.type, record.packet.remaining.toInt(), outputCounter, outputCounter
        )

        val packet = record.packet.encrypted(cipher, outputCounter)
        outputCounter++

        return TLSRecord(record.type, packet = packet)
    }

    override fun decrypt(record: TLSRecord): TLSRecord {
        val packet = record.packet
        val packetSize = packet.remaining
        val recordIv = packet.readLong()

        val cipher = decryptCipher(
            suite, key, record.type, packetSize.toInt(), recordIv, inputCounter++
        )

        return TLSRecord(record.type, record.version, packet.decrypted(cipher))
    }
}
