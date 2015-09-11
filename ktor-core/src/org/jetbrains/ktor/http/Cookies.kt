package org.jetbrains.ktor.http.cookies

import org.jetbrains.ktor.application.*
import org.jetbrains.ktor.http.*
import java.net.*
import java.time.*
import java.time.temporal.*
import java.util.*

public data class Cookie(
        val name: String,
        val value: String,
        val encoding: CookieEncoding = CookieEncoding.URI_ENCODING,
        val maxAge: Int = 0,
        val expires: Temporal? = null,
        val domain: String = "",
        val path: String = "",
        val secure: Boolean = false,
        val httpOnly: Boolean = false,
        val extensions: Map<String, String?> = emptyMap()
)

public enum class CookieEncoding {
    RAW, DQUOTES, URI_ENCODING, BASE64_ENCODING
}

public open class RequestCookies(val request: ApplicationRequest) {
    private val defaultDecoded: Map<String, String> by lazy { decode(CookieEncoding.URI_ENCODING) }

    public fun get(name: String): String? = defaultDecoded[name]
    public fun decode(encoding: CookieEncoding): Map<String, String> = request.attributes.computeIfAbsent(CookiesKey(encoding)) {
        parsedRawCookies.mapValues { decodeCookieValue(it.value, encoding) }
    }

    public open val parsedRawCookies: Map<String, String> by lazy {
        request.headers["Cookie"]?.fold(HashMap<String, String>()) { acc,e -> acc.putAll(parseClientCookiesHeader(e)); acc } ?: emptyMap<String, String>()
    }
}

public class ResponseCookies(val response: ApplicationResponse) {
    public fun get(name: String): Cookie? = response.headers.values("Set-Cookie").map { parseServerSetCookieHeader(it) }.firstOrNull { it.name == name }
    public fun append(item: Cookie): Unit = response.headers.append("Set-Cookie", renderSetCookieHeader(item))
    public fun intercept(handler: (cookie: Cookie, next: (value: Cookie) -> Unit) -> Unit) {
        response.headers.intercept { name, value, next ->
            if (name == "Set-Cookie") {
                handler(parseServerSetCookieHeader(value)) { intercepted ->
                    next(name, renderSetCookieHeader(intercepted))
                }
            } else {
                next(name, value)
            }
        }
    }

    public fun append(name: String,
                                          value: String,
                                          encoding: CookieEncoding = CookieEncoding.URI_ENCODING,
                                          maxAge: Int = 0,
                                          expires: Temporal? = null,
                                          domain: String = "",
                                          path: String = "",
                                          secure: Boolean = false,
                                          httpOnly: Boolean = false,
                                          extensions: Map<String, String?> = emptyMap()) {
        append(Cookie(
                name,
                value,
                encoding,
                maxAge,
                expires,
                domain,
                path,
                secure,
                httpOnly,
                extensions
        ))
    }

    public fun appendExpired(name: String, domain: String = "", path: String = "") {
        append(name, "", domain = domain, path = path, expires = Instant.EPOCH)
    }
}

private val loweredPartNames = setOf("max-age", "expires", "domain", "path", "secure", "httponly", "\$x-enc")
public fun parseServerSetCookieHeader(cookiesHeader: String): Cookie {
    val asMap = parseClientCookiesHeader(cookiesHeader, false)
    val first = asMap.entrySet().first { !it.key.startsWith("$") }
    val encoding = asMap["\$x-enc"]?.let { CookieEncoding.valueOf(it) } ?: CookieEncoding.URI_ENCODING
    val loweredMap = asMap.mapKeys { it.key.toLowerCase() }

    return Cookie(
            name = first.key,
            value = decodeCookieValue(first.value, encoding),
            encoding = encoding,
            maxAge = loweredMap["max-age"]?.toInt() ?: 0,
            expires = loweredMap["expires"]?.let { it.fromHttpDateString() },
            domain = loweredMap["domain"] ?: "",
            path = loweredMap["path"] ?: "",
            secure = "secure" in loweredMap,
            httpOnly = "httponly" in loweredMap,
            extensions = asMap.filterKeys {
                it.toLowerCase() !in loweredPartNames && it != first.key
            }
    )
}

public fun parseClientCookiesHeader(cookiesHeader: String, skipEscaped: Boolean = true): Map<String, String> {
    val pattern = """(^|;|,)\s*([^()<>@,;:/\\"\[\]\?=\{\}\s]+)\s*(=\s*("[^"]*"|[^;,]*))?""".toRegex()

    return pattern.matchAll(cookiesHeader)
        .map { (it.groups[2]?.value ?: "") to (it.groups[4]?.value ?: "") }
        .filter { !skipEscaped || !it.first.startsWith("$") }
        .map { when {
            it.second.startsWith("\"") && it.second.endsWith("\"") -> it.copy(second = it.second.removeSurrounding("\""))
            else -> it
        } }
        .toMap()
}

public fun renderSetCookieHeader(cookie: Cookie): String = with(cookie) {
    renderSetCookieHeader(
            name,
            value,
            encoding,
            maxAge,
            expires,
            domain,
            path,
            secure,
            httpOnly,
            extensions
    )
}

public fun renderSetCookieHeader(name: String,
                                 value: String,
                                 encoding: CookieEncoding = CookieEncoding.URI_ENCODING,
                                 maxAge: Int = 0,
                                 expires: Temporal? = null,
                                 domain: String = "",
                                 path: String = "",
                                 secure: Boolean = false,
                                 httpOnly: Boolean = false,
                                 extensions: Map<String, String?> = emptyMap()): String =
        (listOf(
                cookiePart(name.assertCookieName(), value, encoding),
                cookiePartUnencoded("Max-Age", if (maxAge > 0) maxAge else null),
                cookiePartUnencoded("Expires", expires?.toHttpDateString()),
                cookiePart("Domain", domain.nullIfEmpty(), CookieEncoding.RAW),
                cookiePart("Path", path.nullIfEmpty(), CookieEncoding.RAW),

                cookiePartFlag("Secure", secure),
                cookiePartFlag("HttpOnly", httpOnly)
        ) + extensions.map {
            cookiePartExt(it.key.assertCookieName(), it.value, encoding)
        } + cookiePartExt("\$x-enc", encoding.name(), CookieEncoding.RAW)
                ).filter { it.isNotEmpty() }
                .joinToString("; ")

public fun encodeCookieValue(value: String, encoding: CookieEncoding): String =
        when (encoding) {
            CookieEncoding.RAW -> when {
                value.any { it.shouldEscapeInCookies() } -> throw IllegalArgumentException("The cookie value contains characters that couldn't be encoded in RAW format. Consider URL_ENCODING mode")
                else -> value
            }
            CookieEncoding.DQUOTES -> when {
                value.contains('"') -> throw IllegalArgumentException("The cookie value contains characters that couldn't be encoded in RAW format. Consider URL_ENCODING mode")
                value.any { it.shouldEscapeInCookies() } -> "\"$value\""
                else -> value
            }
            CookieEncoding.BASE64_ENCODING -> Base64.getEncoder().encodeToString(value.toByteArray(Charsets.UTF_8))
            CookieEncoding.URI_ENCODING -> URLEncoder.encode(value, "UTF-8")
        }

public fun decodeCookieValue(encodedValue: String, encoding: CookieEncoding): String =
        when (encoding) {
            CookieEncoding.RAW, CookieEncoding.DQUOTES -> when {
                encodedValue.trimStart().startsWith("\"") && encodedValue.trimEnd().endsWith("\"") -> encodedValue.trim().removeSurrounding("\"")
                else -> encodedValue
            }
            CookieEncoding.URI_ENCODING -> URLDecoder.decode(encodedValue, "UTF-8")
            CookieEncoding.BASE64_ENCODING -> Base64.getDecoder().decode(encodedValue).toString(Charsets.UTF_8)
        }

private fun String.assertCookieName() = when {
    any { it.shouldEscapeInCookies() } -> throw IllegalArgumentException("Cookie name is not valid: $this")
    else -> this
}

private val cookieCharsShouldBeEscaped = setOf(';', ',', '=', '"')
private fun Char.shouldEscapeInCookies() = this.isWhitespace() || this < ' ' || this in cookieCharsShouldBeEscaped

@Suppress("NOTHING_TO_INLINE")
private inline fun cookiePart(name: String, value: Any?, encoding: CookieEncoding) =
        if (value != null) "$name=${encodeCookieValue(value.toString(), encoding)}" else ""

@Suppress("NOTHING_TO_INLINE")
private inline fun cookiePartUnencoded(name: String, value: Any?) =
        if (value != null) "$name=${value.toString()}" else ""


@Suppress("NOTHING_TO_INLINE")
private inline fun cookiePartFlag(name: String, value: Boolean) =
        if (value) name else ""

@Suppress("NOTHING_TO_INLINE")
private inline fun cookiePartExt(name: String, value: String?, encoding: CookieEncoding) =
        if (value == null) cookiePartFlag(name, true) else cookiePart(name, value, encoding)

@Suppress("NOTHING_TO_INLINE")
private inline fun String.nullIfEmpty() = if (this.isEmpty()) null else this

private data class CookiesKey(val encoding: CookieEncoding) : AttributeKey<Map<String, String>>()

