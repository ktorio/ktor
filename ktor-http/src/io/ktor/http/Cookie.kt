package io.ktor.http

import io.ktor.compat.*

data class Cookie(
    val name: String,
    val value: String,
    val encoding: CookieEncoding = CookieEncoding.URI_ENCODING,
    val maxAge: Int = 0,
    val expires: /* Temporal */String? = null,
    val domain: String? = null,
    val path: String? = null,
    val secure: Boolean = false,
    val httpOnly: Boolean = false,
    val extensions: Map<String, String?> = emptyMap()
)

enum class CookieEncoding {
    RAW, DQUOTES, URI_ENCODING, BASE64_ENCODING
}

private val loweredPartNames = setOf("max-age", "expires", "domain", "path", "secure", "httponly", "\$x-enc")
fun parseServerSetCookieHeader(cookiesHeader: String): Cookie {
    val asMap = parseClientCookiesHeader(cookiesHeader, false)
    val first = asMap.entries.first { !it.key.startsWith("$") }
    val encoding = asMap["\$x-enc"]?.let { CookieEncoding.valueOf(it) } ?: CookieEncoding.URI_ENCODING
    val loweredMap = asMap.mapKeys { it.key.toLowerCase() }

    return Cookie(
        name = first.key,
        value = decodeCookieValue(first.value, encoding),
        encoding = encoding,
        maxAge = loweredMap["max-age"]?.toInt() ?: 0,
        expires = loweredMap["expires"]/*?.fromHttpDateString()*/,
        domain = loweredMap["domain"],
        path = loweredMap["path"],
        secure = "secure" in loweredMap,
        httpOnly = "httponly" in loweredMap,
        extensions = asMap.filterKeys {
            it.toLowerCase() !in loweredPartNames && it != first.key
        }
    )
}

private val clientCookieHeaderPattern = """(^|;)\s*([^()<>@;:/\\"\[\]\?=\{\}\s]+)\s*(=\s*("[^"]*"|[^;]*))?""".toRegex()
fun parseClientCookiesHeader(cookiesHeader: String, skipEscaped: Boolean = true): Map<String, String> =
    clientCookieHeaderPattern.findAll(cookiesHeader)
        .map { (it.groups[2]?.value ?: "") to (it.groups[4]?.value ?: "") }
        .filter { !skipEscaped || !it.first.startsWith("$") }
        .map {
            if (it.second.startsWith("\"") && it.second.endsWith("\""))
                it.copy(second = it.second.removeSurrounding("\""))
            else it
        }
        .toMap()

fun renderSetCookieHeader(cookie: Cookie): String = with(cookie) {
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

fun renderCookieHeader(cookie: Cookie): String = with(cookie) {
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
        extensions,
        includeEncoding = false
    )
}

fun renderSetCookieHeader(
    name: String, value: String,
    encoding: CookieEncoding = CookieEncoding.URI_ENCODING,
    maxAge: Int = 0, expires: /*Temporal?*/String? = null, domain: String? = null,
    path: String? = null,
    secure: Boolean = false, httpOnly: Boolean = false,
    extensions: Map<String, String?> = emptyMap()
,
    includeEncoding: Boolean = true
): String = (
        listOf(
            cookiePart(name.assertCookieName(), value, encoding),
            cookiePartUnencoded("Max-Age", if (maxAge > 0) maxAge else null),
            cookiePartUnencoded("Expires", expires/*?.toHttpDateString()*/),
            cookiePart("Domain", domain, CookieEncoding.RAW),
            cookiePart("Path", path, CookieEncoding.RAW),

            cookiePartFlag("Secure", secure),
            cookiePartFlag("HttpOnly", httpOnly)
        )
                + extensions.map { cookiePartExt(it.key.assertCookieName(), it.value, encoding) }
                + if (includeEncoding) cookiePartExt("\$x-enc", encoding.name, CookieEncoding.RAW) else ""
        ).filter { it.isNotEmpty() }
    .joinToString("; ")

fun encodeCookieValue(value: String, encoding: CookieEncoding): String = when (encoding) {
    CookieEncoding.RAW -> when {
        value.any { it.shouldEscapeInCookies() } -> throw IllegalArgumentException("The cookie value contains characters that couldn't be encoded in RAW format. Consider URL_ENCODING mode")
        else -> value
    }
    CookieEncoding.DQUOTES -> when {
        value.contains('"') -> throw IllegalArgumentException("The cookie value contains characters that couldn't be encoded in RAW format. Consider URL_ENCODING mode")
        value.any { it.shouldEscapeInCookies() } -> "\"$value\""
        else -> value
    }
    CookieEncoding.BASE64_ENCODING -> encodeBase64(value)
    CookieEncoding.URI_ENCODING -> encodeURLQueryComponent(value)
}

fun decodeCookieValue(encodedValue: String, encoding: CookieEncoding): String = when (encoding) {
    CookieEncoding.RAW, CookieEncoding.DQUOTES -> when {
        encodedValue.trimStart().startsWith("\"") && encodedValue.trimEnd().endsWith("\"") ->
            encodedValue.trim().removeSurrounding("\"")
        else -> encodedValue
    }
    CookieEncoding.URI_ENCODING -> decodeURLQueryComponent(encodedValue)
    CookieEncoding.BASE64_ENCODING -> decodeBase64(encodedValue)
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
    if (value != null) "$name=$value" else ""


@Suppress("NOTHING_TO_INLINE")
private inline fun cookiePartFlag(name: String, value: Boolean) =
    if (value) name else ""

@Suppress("NOTHING_TO_INLINE")
private inline fun cookiePartExt(name: String, value: String?, encoding: CookieEncoding) =
    if (value == null) cookiePartFlag(name, true) else cookiePart(name, value, encoding)
