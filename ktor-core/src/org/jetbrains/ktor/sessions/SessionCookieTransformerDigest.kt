package org.jetbrains.ktor.sessions

import org.jetbrains.ktor.util.*
import java.security.*

class SessionCookieTransformerDigest(val salt: String = "ktor", val algorithm: String = "SHA-256") : SessionCookieTransformer {
    override fun transformRead(sessionCookieValue: String): String? {
        val expectedSignature = sessionCookieValue.substringAfterLast('/', "")
        val value = sessionCookieValue.substringBeforeLast('/')
        if (expectedSignature == digest(value))
            return value
        return null
    }

    override fun transformWrite(sessionCookieValue: String): String = "$sessionCookieValue/${digest(sessionCookieValue)}"

    private fun digest(value: String): String {
        val md = MessageDigest.getInstance(algorithm)
        md.update(salt.toByteArray())
        md.update(value.toByteArray())
        return hex(md.digest())
    }
}
