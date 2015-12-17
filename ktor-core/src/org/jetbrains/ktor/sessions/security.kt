package org.jetbrains.ktor.sessions

import org.jetbrains.ktor.auth.crypto.*
import java.security.*
import javax.crypto.*
import javax.crypto.spec.*

class DigestCookieTransformer(val salt: String = "ktor", val algorithm: String = "SHA-256") : CookieTransformer {
    override fun transformRead(sessionCookieValue: String): String? {
        val expectedSignature = sessionCookieValue.substringAfterLast('/', "")
        val value = sessionCookieValue.substringBeforeLast('/')

        return if (digest(value) == expectedSignature) value else null
    }

    override fun transformWrite(sessionCookieValue: String): String = "$sessionCookieValue/${digest(sessionCookieValue)}"

    private fun digest(value: String): String {
        val md = MessageDigest.getInstance(algorithm)
        md.update(salt.toByteArray())
        md.update(value.toByteArray())
        return hex(md.digest())
    }
}

class MessageAuthenticationCookieTransformer(val key: ByteArray, val algorithm: String = "HmacSHA1") : CookieTransformer {
    private val keySpec = SecretKeySpec(key, algorithm)

    override fun transformRead(sessionCookieValue: String): String? {
        val expectedSignature = sessionCookieValue.substringAfterLast('/', "")
        val value = sessionCookieValue.substringBeforeLast('/')

        return if (mac(value) == expectedSignature) value else null
    }

    override fun transformWrite(sessionCookieValue: String): String = "$sessionCookieValue/${mac(sessionCookieValue)}"

    private fun mac(value: String): String {
        val mac = Mac.getInstance("HmacSHA1")
        mac.init(keySpec)

        return hex(mac.doFinal(value.toByteArray()))
    }
}

