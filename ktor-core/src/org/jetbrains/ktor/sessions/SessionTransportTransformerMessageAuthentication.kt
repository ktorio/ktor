package org.jetbrains.ktor.sessions

import org.jetbrains.ktor.util.*
import javax.crypto.*
import javax.crypto.spec.*

class SessionTransportTransformerMessageAuthentication(val keySpec: SecretKeySpec, val algorithm: String = "HmacSHA1") : SessionTransportTransformer {
    constructor(key: ByteArray, algorithm: String = "HmacSHA1") : this(SecretKeySpec(key, algorithm), algorithm)

    override fun transformRead(transportValue: String): String? {
        val expectedSignature = transportValue.substringAfterLast('/', "")
        val value = transportValue.substringBeforeLast('/')
        if (mac(value) == expectedSignature)
            return value
        return null
    }

    override fun transformWrite(transportValue: String): String = "$transportValue/${mac(transportValue)}"

    private fun mac(value: String): String {
        val mac = Mac.getInstance(algorithm)
        mac.init(keySpec)

        return hex(mac.doFinal(value.toByteArray()))
    }
}

