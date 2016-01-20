package org.jetbrains.ktor.sessions

import org.jetbrains.ktor.http.*
import java.time.*
import java.time.temporal.*

class SessionCookiesSettings(
        private val duration: TemporalAmount = Duration.ofDays(7),
        private val requireHttps: Boolean = false,
        private val transformers: List<SessionCookieTransformer> = emptyList()
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

    fun toCookie(name: String, value: String) = Cookie(name,
            value = transformers.fold(value) { value, t -> t.transformWrite(value) },
            httpOnly = true,
            secure = requireHttps,
            expires = LocalDateTime.now().plus(duration))
}


