package org.jetbrains.ktor.http

import org.jetbrains.ktor.application.*
import java.util.*

public open class RequestCookies(val request: ApplicationRequest) {
    private val defaultDecoded: Map<String, String> by lazy { decode(CookieEncoding.URI_ENCODING) }

    public fun get(name: String): String? = defaultDecoded[name]
    public fun decode(encoding: CookieEncoding): Map<String, String> = request.attributes.computeIfAbsent(CookiesKey(encoding)) {
        parsedRawCookies.mapValues { decodeCookieValue(it.value, encoding) }
    }

    public open val parsedRawCookies: Map<String, String> by lazy {
        request.headers["Cookie"]?.fold(HashMap<String, String>()) { acc, e -> acc.putAll(parseClientCookiesHeader(e)); acc } ?: emptyMap<String, String>()
    }
}