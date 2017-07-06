package org.jetbrains.ktor.sessions

import org.jetbrains.ktor.util.*
import java.security.*

class SessionTransportTransformerDigest(val salt: String = "ktor", val algorithm: String = "SHA-256") : SessionTransportTransformer {
    override fun transformRead(transportValue: String): String? {
        val expectedSignature = transportValue.substringAfterLast('/', "")
        val value = transportValue.substringBeforeLast('/')
        if (expectedSignature == digest(value))
            return value
        return null
    }

    override fun transformWrite(transportValue: String): String = "$transportValue/${digest(transportValue)}"

    private fun digest(value: String): String {
        val md = MessageDigest.getInstance(algorithm)
        md.update(salt.toByteArray())
        md.update(value.toByteArray())
        return hex(md.digest())
    }
}
