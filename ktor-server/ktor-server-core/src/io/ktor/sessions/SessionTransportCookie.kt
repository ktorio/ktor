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
        call.response.cookies.append(configuration.buildCookie(
            name = name,
            value = transformers.transformWrite(value)
        ))
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

    var sameSite: CookieSameSite?
        get() = extensions[CookieSameSite.KEY]?.run {
            CookieSameSite.values().firstOrNull { it.name.equals(this, ignoreCase = true) }
        }
        set(value) {
            if (value == null) {
                extensions.remove(CookieSameSite.KEY)
            } else {
                extensions[CookieSameSite.KEY] = value.name.toLowerCase()
            }
        }

    fun buildCookie(name: String, value: String) : Cookie {
        val now = GMTDate()
        val maxAge = duration[ChronoUnit.SECONDS].toInt()
        val expires = now + (maxAge.toLong() * 1000)
        return Cookie(
            name, value,
            encoding, maxAge, expires,
            domain, path,
            secure, httpOnly,
            extensions
        )
    }
}
