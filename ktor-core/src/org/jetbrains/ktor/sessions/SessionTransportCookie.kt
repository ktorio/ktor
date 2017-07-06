package org.jetbrains.ktor.sessions

import org.jetbrains.ktor.application.*
import org.jetbrains.ktor.http.*
import java.time.*
import java.time.temporal.*

class SessionTransportCookie(val name: String,
                             val duration: TemporalAmount,
                             val requireHttps: Boolean,
                             val transformers: List<SessionTransportTransformer>
) : SessionTransport {

    override fun receive(call: ApplicationCall): String? {
        return transformers.transformRead(call.request.cookies[name])
    }

    override fun send(call: ApplicationCall, value: String) {
        val cookie = Cookie(name,
                value = transformers.transformWrite(value),
                httpOnly = true,
                secure = requireHttps,
                path = "/",
                expires = LocalDateTime.now().plus(duration))

        call.response.cookies.append(cookie)
    }

    override fun clear(call: ApplicationCall) {
        call.response.cookies.appendExpired(name)
    }
}

