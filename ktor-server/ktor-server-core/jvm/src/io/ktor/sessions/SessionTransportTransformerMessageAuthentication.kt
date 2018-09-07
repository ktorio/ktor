package io.ktor.sessions

import io.ktor.util.*
import javax.crypto.*
import javax.crypto.spec.*

/**
 * Session transformer that appends an [algorithm] MAC (Message Authentication Code) hash of the input.
 * Where the input is either a session contents or a previous transformation.
 * It uses a specified [keySpec] when generating the Mac hash.
 *
 * @property keySpec is a secret key spec for message authentication
 * @property algorithm is a message authentication algorithm name
 */
class SessionTransportTransformerMessageAuthentication(val keySpec: SecretKeySpec, val algorithm: String = "HmacSHA1") :
    SessionTransportTransformer {
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

