package io.ktor.request

import io.ktor.http.*
import java.util.*
import java.util.concurrent.*

open class RequestCookies(val request: ApplicationRequest) {
    private val map = ConcurrentHashMap<Pair<CookieEncoding, String>, String>()
    val rawCookies: Map<String, String> by lazy { fetchCookies() }

    operator fun get(name: String, encoding: CookieEncoding = CookieEncoding.URI_ENCODING): String? {
        val rawValue = rawCookies[name] ?: return null
        return map.computeIfAbsent(encoding to name) { decodeCookieValue(rawValue, encoding) }
    }

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