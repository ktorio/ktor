package org.jetbrains.ktor.sessions

import org.jetbrains.ktor.application.*
import org.jetbrains.ktor.http.*
import java.time.*
import java.time.temporal.*

class SessionTransportCookie(val name: String,
                             val duration: TemporalAmount = Duration.ofDays(7),
                             val requireHttps: Boolean = false,
                             val transformers: List<SessionTransportTransformer> = emptyList()
) : SessionTransport {
    private fun fromCookie(cookieValue: String?): String? {
        var value = cookieValue
        for (t in transformers) {
            if (value == null) {
                break
            }
            value = t.transformRead(value)
        }
        return value
    }

    private fun toCookie(value: String): Cookie {
        val cookie = Cookie(name,
                value = transformers.fold(value) { it, transformer -> transformer.transformWrite(it) },
                httpOnly = true,
                secure = requireHttps,
                path = "/",
                expires = LocalDateTime.now().plus(duration))
        return cookie
    }

    override fun receive(call: ApplicationCall): String? {
        return fromCookie(call.request.cookies[name])
    }

    override fun send(call: ApplicationCall, value: String) {
        call.response.cookies.append(toCookie(value))
    }

    override fun clear(call: ApplicationCall) {
        call.response.cookies.appendExpired(name)
    }
}