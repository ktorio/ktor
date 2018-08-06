package io.ktor.sessions

import io.ktor.application.*
import io.ktor.http.*
import io.ktor.util.date.*
import java.time.*
import java.time.temporal.*

/**
 * SessionTransport that adds a Set-Cookie header and reads Cookie header
 * for the specified cookie [name], and a specific cookie [configuration] after
 * applying/un-applying the specified transforms defined by [transformers].
 */
class SessionTransportCookie(
    val name: String,
    val configuration: CookieConfiguration,
    val transformers: List<SessionTransportTransformer>
) : SessionTransport {

    override fun receive(call: ApplicationCall): String? {
        return transformers.transformRead(call.request.cookies[name])
    }

    override fun send(call: ApplicationCall, value: String) {
        val now = GMTDate()
        val maxAge = configuration.duration[ChronoUnit.SECONDS].toInt()
        val expires = now + (maxAge.toLong() * 1000)
        val cookie = Cookie(
            name,
            transformers.transformWrite(value),
            configuration.encoding,
            maxAge,
            expires,
            configuration.domain,
            configuration.path,
            configuration.secure,
            configuration.httpOnly,
            configuration.extensions
        )

        call.response.cookies.append(cookie)
    }

    override fun clear(call: ApplicationCall) {
        call.response.cookies.appendExpired(name, configuration.domain, configuration.path)
    }
}

class CookieConfiguration {
    var duration: TemporalAmount = Duration.ofDays(7)
    var encoding: CookieEncoding = CookieEncoding.URI_ENCODING
    var domain: String? = null
    var path: String? = null
    var secure: Boolean = false
    var httpOnly: Boolean = false
    val extensions: MutableMap<String, String?> = mutableMapOf()
}
