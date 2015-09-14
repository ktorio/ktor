package org.jetbrains.ktor.http

import org.jetbrains.ktor.application.*
import java.util.*
import java.util.concurrent.*

public open class RequestCookies(private val request: ApplicationRequest) {
    private val map = ConcurrentHashMap<Pair<CookieEncoding, String>, String>()

    // TODO: Remove in favor of default parameter value when KT-9140 is fixed
    public fun get(name: String): String? = get(name, CookieEncoding.URI_ENCODING)

    public fun get(name: String, encoding: CookieEncoding): String? {
        val rawValue = parsedRawCookies[name] ?: return null
        return map.computeIfAbsent(encoding to name) { decodeCookieValue(rawValue, encoding) }
    }

    public open val parsedRawCookies: Map<String, String> by lazy {
        request.headers.getAll("Cookie")?.fold(HashMap<String, String>()) { acc, e -> acc.putAll(parseClientCookiesHeader(e)); acc }
                ?: emptyMap<String, String>()
    }
}