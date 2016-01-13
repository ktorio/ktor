package org.jetbrains.ktor.http

import org.jetbrains.ktor.application.*
import java.util.*
import java.util.concurrent.*

public open class RequestCookies(private val request: ApplicationRequest) {
    private val map = ConcurrentHashMap<Pair<CookieEncoding, String>, String>()

    public operator fun get(name: String, encoding: CookieEncoding = CookieEncoding.URI_ENCODING): String? {
        val rawValue = parsedRawCookies[name] ?: return null
        return map.computeIfAbsent(encoding to name) { decodeCookieValue(rawValue, encoding) }
    }

    public open val parsedRawCookies: Map<String, String> by lazy {
        request.headers.getAll("Cookie")?.fold(HashMap<String, String>()) { acc, e -> acc.putAll(parseClientCookiesHeader(e)); acc }
                ?: emptyMap<String, String>()
    }
}