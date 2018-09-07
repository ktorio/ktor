package io.ktor.request

import io.ktor.http.*
import io.ktor.util.*
import java.util.*
import java.util.concurrent.*

/**
 * Server request's cookies
 * @property request application request to fetch cookies from
 */
open class RequestCookies(protected val request: ApplicationRequest) {
    private val map = ConcurrentHashMap<Pair<CookieEncoding, String>, String>()

    /**
     * RAW cookie values, not decoded so could have percent encoded values, quotes, escape characters and so on.
     * It is recommended to use [get] instead
     */
    @KtorExperimentalAPI
    val rawCookies: Map<String, String> by lazy { fetchCookies() }

    /**
     * Get cookie [name] value decoding cookies using [encoding] strategy
     */
    operator fun get(name: String, encoding: CookieEncoding = CookieEncoding.URI_ENCODING): String? {
        val rawValue = rawCookies[name] ?: return null
        return map.computeIfAbsent(encoding to name) { decodeCookieValue(rawValue, encoding) }
    }

    /**
     * Fetch cookies from [request]. Could access cookies using engine's native API.
     */
    protected open fun fetchCookies(): Map<String, String> {
        val cookieHeaders = request.headers.getAll("Cookie") ?: return emptyMap()
        val map = HashMap<String, String>(cookieHeaders.size)
        for (cookieHeader in cookieHeaders) {
            val cookies = parseClientCookiesHeader(cookieHeader)
            map.putAll(cookies)
        }
        return map
    }
}
