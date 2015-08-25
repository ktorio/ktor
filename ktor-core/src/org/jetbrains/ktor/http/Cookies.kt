package org.jetbrains.ktor.http.cookies

import org.jetbrains.ktor.application.*
import org.jetbrains.ktor.http.*
import java.net.*
import java.time.*
import java.time.temporal.*
import java.util.*

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

public fun parseClientCookiesHeader(cookiesHeader: String): Map<String, String> =
        cookiesHeader.trimStart().split("[;,]\\s*".toRegex())
                .filter { !it.startsWith("$") && '=' in it }
                .map { it.split("=") }
                .toMap({ it[0].trim() }, { it[1] })

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
        }).filter { it.isNotEmpty() }
                .joinToString("; ")


public fun ApplicationResponse.setCookie(name: String,
                                         value: String,
                                         encoding: CookieEncoding = CookieEncoding.URI_ENCODING,
                                         maxAge: Int = 0,
                                         expires: Temporal? = null,
                                         domain: String = "",
                                         path: String = "",
                                         secure: Boolean = false,
                                         httpOnly: Boolean = false,
                                         extensions: Map<String, String?> = emptyMap()) {
    header("Set-Cookie", renderSetCookieHeader(name, value, encoding, maxAge, expires, domain, path, secure, httpOnly, extensions))
}

public fun ApplicationResponse.setCookieExpired(name: String, domain: String = "", path: String = "") {
    setCookie(name, "", domain = domain, path = path, expires = Instant.EPOCH)
}

public fun ApplicationRequest.getOrSetCookie(response: ApplicationResponse,
                                             name: String,
                                             encoding: CookieEncoding = CookieEncoding.URI_ENCODING,
                                             maxAge: Int = 0,
                                             expires: Temporal? = null,
                                             domain: String = "",
                                             path: String = "",
                                             secure: Boolean = false,
                                             httpOnly: Boolean = false,
                                             extensions: Map<String, String?> = emptyMap(),
                                             provider: () -> String): String {
    val requestCookieValue = cookies[name]
    if (requestCookieValue == null) {
        val newValue = provider()
        response.setCookie(name, newValue, encoding, maxAge, expires, domain, path, secure, httpOnly, extensions)
        return newValue
    }

    return requestCookieValue
}

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

suppress("NOTHING_TO_INLINE")
private inline fun cookiePart(name: String, value: Any?, encoding: CookieEncoding) =
        if (value != null) "$name=${encodeCookieValue(value.toString(), encoding)}" else ""

suppress("NOTHING_TO_INLINE")
private inline fun cookiePartUnencoded(name: String, value: Any?) =
        if (value != null) "$name=${value.toString()}" else ""


suppress("NOTHING_TO_INLINE")
private inline fun cookiePartFlag(name: String, value: Boolean) =
        if (value) name else ""

suppress("NOTHING_TO_INLINE")
private inline fun cookiePartExt(name: String, value: String?, encoding: CookieEncoding) =
        if (value == null) cookiePartFlag(name, true) else cookiePart(name, value, encoding)

suppress("NOTHING_TO_INLINE")
private inline fun String.nullIfEmpty() = if (this.isEmpty()) null else this

private data class CookiesKey(val encoding: CookieEncoding) : AttributeKey<Map<String, String>>()

