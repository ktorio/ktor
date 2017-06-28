package org.jetbrains.ktor.sessions

import org.jetbrains.ktor.http.*
import java.time.*
import java.time.temporal.*

class SessionCookiesSettings(
        private val duration: TemporalAmount,
        private val requireHttps: Boolean,
        private val transformers: List<SessionCookieTransformer>
) {

    fun fromCookie(cookieValue: String?): String? {
        var value = cookieValue
        for (t in transformers) {
            if (value == null) {
                break
            }
            value = t.transformRead(value)
        }
        return value
    }

    fun toCookie(name: String, value: String): Cookie {
        val cookie = Cookie(name,
                value = transformers.fold(value) { it, transformer -> transformer.transformWrite(it) },
                httpOnly = true,
                secure = requireHttps,
                path = "/",
                expires = LocalDateTime.now().plus(duration))
        return cookie
    }
}

