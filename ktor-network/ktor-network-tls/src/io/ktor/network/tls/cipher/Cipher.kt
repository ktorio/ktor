package io.ktor.network.tls.cipher

import io.ktor.network.tls.*

internal interface TLSCipher {

    fun encrypt(record: TLSRecord): TLSRecord
    fun decrypt(record: TLSRecord): TLSRecord

    companion object {
        fun fromSuite(suite: CipherSuite, key: ByteArray): TLSCipher = when (suite.cipherType) {
            CipherType.GCM -> GCMCipher(suite, key)
            else -> TODO()
        }
    }
}

internal class CBCCipher {
}
