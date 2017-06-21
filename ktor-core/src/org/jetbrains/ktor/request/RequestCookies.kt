package org.jetbrains.ktor.request

import org.jetbrains.ktor.http.*
import java.util.*
import java.util.concurrent.*

open class RequestCookies(private val request: ApplicationRequest) {
    private val map = ConcurrentHashMap<Pair<CookieEncoding, String>, String>()

    operator fun get(name: String, encoding: CookieEncoding = CookieEncoding.URI_ENCODING): String? {
        val rawValue = parsedRawCookies[name] ?: return null
        return map.computeIfAbsent(encoding to name) { decodeCookieValue(rawValue, encoding) }
    }

    open val parsedRawCookies: Map<String, String> by lazy {
        request.headers.getAll("Cookie")?.fold(HashMap<String, String>()) { acc, e -> acc.putAll(parseClientCookiesHeader(e)); acc }
                ?: emptyMap<String, String>()
    }
}