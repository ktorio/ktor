package org.jetbrains.ktor.sessions

import org.jetbrains.ktor.util.*
import javax.crypto.*
import javax.crypto.spec.*

class SessionCookieTransformerMessageAuthentication(val keySpec: SecretKeySpec, val algorithm: String = "HmacSHA1") : SessionCookieTransformer {
    constructor(key: ByteArray, algorithm: String = "HmacSHA1") : this(SecretKeySpec(key, algorithm), algorithm)

    override fun transformRead(sessionCookieValue: String): String? {
        val expectedSignature = sessionCookieValue.substringAfterLast('/', "")
        val value = sessionCookieValue.substringBeforeLast('/')
        if (mac(value) == expectedSignature)
            return value
        return null
    }

    override fun transformWrite(sessionCookieValue: String): String = "$sessionCookieValue/${mac(sessionCookieValue)}"

    private fun mac(value: String): String {
        val mac = Mac.getInstance(algorithm)
        mac.init(keySpec)

        return hex(mac.doFinal(value.toByteArray()))
    }
}

