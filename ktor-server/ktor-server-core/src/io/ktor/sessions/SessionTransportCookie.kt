package io.ktor.sessions

import io.ktor.application.*
import io.ktor.http.*
import io.ktor.util.date.*
import java.time.*
import java.time.temporal.*
import java.util.concurrent.*

/**
 * SessionTransport that adds a Set-Cookie header and reads Cookie header
 * for the specified cookie [name], and a specific cookie [configuration] after
 * applying/un-applying the specified transforms defined by [transformers].
 *
 * @property name is a cookie name
 * @property configuration is a cookie configuration
 * @property transformers is a list of session transformers
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
        val maxAge = configuration.duration?.let { it[ChronoUnit.SECONDS] }?.takeIf { it <= Int.MAX_VALUE }
        val expires = maxAge?.let { now + TimeUnit.SECONDS.toMillis(maxAge) }

        val cookie = Cookie(
            name,
            transformers.transformWrite(value),
            configuration.encoding,
            maxAge?.toInt() ?: 0,
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

/**
 * Cookie configuration being used to send sessions
 */
class CookieConfiguration {
    /**
     * Cookie time to live duration
     */
    var duration: TemporalAmount? = Duration.ofDays(7)

    /**
     * Cookie encoding
     */
    var encoding: CookieEncoding = CookieEncoding.URI_ENCODING

    /**
     * Cookie domain
     */
    var domain: String? = null

    /**
     * Cookie path
     */
    var path: String? = null

    /**
     * Send cookies only over secure connection
     */
    var secure: Boolean = false

    /**
     * This cookie is only for transferring over HTTP(s) and shouldn't be accessible via JavaScript
     */
    var httpOnly: Boolean = false

    /**
     * Any additional extra cookie parameters
     */
    val extensions: MutableMap<String, String?> = mutableMapOf()
}
