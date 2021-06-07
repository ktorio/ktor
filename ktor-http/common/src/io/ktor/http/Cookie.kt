/*
* Copyright 2014-2021 JetBrains s.r.o and contributors. Use of this source code is governed by the Apache 2.0 license.
*/

package io.ktor.http

import io.ktor.util.*
import io.ktor.util.date.*
import kotlin.jvm.*
import kotlin.native.concurrent.*

/**
 * Represents a cookie with name, content and a set of settings such as expiration, visibility and security.
 * A cookie with neither [expires] nor [maxAge] is a session cookie.
 *
 * @property name
 * @property value
 * @property encoding - cookie encoding type [CookieEncoding]
 * @property maxAge number of seconds to keep cookie
 * @property expires date when it expires
 * @property domain for which it is set
 * @property path for which it is set
 * @property secure send it via secure connection only
 * @property httpOnly only transfer cookie over HTTP, no access from JavaScript
 * @property extensions additional cookie extensions
 */
public data class Cookie(
    val name: String,
    val value: String,
    val encoding: CookieEncoding = CookieEncoding.URI_ENCODING,
    @get:JvmName("getMaxAgeInt")
    val maxAge: Int = 0,
    val expires: GMTDate? = null,
    val domain: String? = null,
    val path: String? = null,
    val secure: Boolean = false,
    val httpOnly: Boolean = false,
    val extensions: Map<String, String?> = emptyMap()
) {
    @Suppress("unused", "KDocMissingDocumentation")
    @Deprecated("Binary compatibility.", level = DeprecationLevel.HIDDEN)
    public fun getMaxAge(): Int = maxAge
}

/**
 * Cooke encoding strategy
 */
public enum class CookieEncoding {
    /**
     * No encoding (could be dangerous)
     */
    RAW,

    /**
     * Double quotes with slash-escaping
     */
    DQUOTES,

    /**
     * URI encoding
     */
    URI_ENCODING,

    /**
     * BASE64 encoding
     */
    BASE64_ENCODING
}

@SharedImmutable
private val loweredPartNames = setOf("max-age", "expires", "domain", "path", "secure", "httponly", "\$x-enc")

/**
 * Parse server's `Set-Cookie` header value
 */
public fun parseServerSetCookieHeader(cookiesHeader: String): Cookie {
    val asMap = parseClientCookiesHeader(cookiesHeader, false)
    val first = asMap.entries.first { !it.key.startsWith("$") }
    val encoding = asMap["\$x-enc"]?.let { CookieEncoding.valueOf(it) } ?: CookieEncoding.RAW
    val loweredMap = asMap.mapKeys { it.key.toLowerCasePreservingASCIIRules() }

    return Cookie(
        name = first.key,
        value = decodeCookieValue(first.value, encoding),
        encoding = encoding,
        maxAge = loweredMap["max-age"]?.toIntClamping() ?: 0,
        expires = loweredMap["expires"]?.fromCookieToGmtDate(),
        domain = loweredMap["domain"],
        path = loweredMap["path"],
        secure = "secure" in loweredMap,
        httpOnly = "httponly" in loweredMap,
        extensions = asMap.filterKeys {
            it.toLowerCasePreservingASCIIRules() !in loweredPartNames && it != first.key
        }
    )
}

@ThreadLocal
private val clientCookieHeaderPattern = """(^|;)\s*([^()<>@;:/\\"\[\]\?=\{\}\s]+)\s*(=\s*("[^"]*"|[^;]*))?""".toRegex()

/**
 * Parse client's `Cookie` header value
 */
public fun parseClientCookiesHeader(cookiesHeader: String, skipEscaped: Boolean = true): Map<String, String> =
    clientCookieHeaderPattern.findAll(cookiesHeader)
        .map { (it.groups[2]?.value ?: "") to (it.groups[4]?.value ?: "") }
        .filter { !skipEscaped || !it.first.startsWith("$") }
        .map { cookie ->
            if (cookie.second.startsWith("\"") && cookie.second.endsWith("\"")) {
                cookie.copy(second = cookie.second.removeSurrounding("\""))
            } else {
                cookie
            }
        }
        .toMap()

/**
 * Format `Set-Cookie` header value
 */
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

/**
 * Format `Set-Cookie` header value
 */
public fun renderCookieHeader(cookie: Cookie): String = with(cookie) {
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

/**
 * Format `Set-Cookie` header value
 */
public fun renderSetCookieHeader(
    name: String,
    value: String,
    encoding: CookieEncoding = CookieEncoding.URI_ENCODING,
    maxAge: Int = 0,
    expires: GMTDate? = null,
    domain: String? = null,
    path: String? = null,
    secure: Boolean = false,
    httpOnly: Boolean = false,
    extensions: Map<String, String?> = emptyMap(),
    includeEncoding: Boolean = true
): String = (
    listOf(
        cookiePart(name.assertCookieName(), value, encoding),
        cookiePartUnencoded("Max-Age", if (maxAge > 0) maxAge else null),
        cookiePartUnencoded("Expires", expires?.toHttpDate()),
        cookiePart("Domain", domain, CookieEncoding.RAW),
        cookiePart("Path", path, CookieEncoding.RAW),

        cookiePartFlag("Secure", secure),
        cookiePartFlag("HttpOnly", httpOnly)
    ) + extensions.map { cookiePartExt(it.key.assertCookieName(), it.value, encoding) } +
        if (includeEncoding) cookiePartExt("\$x-enc", encoding.name, CookieEncoding.RAW) else ""
    ).filter { it.isNotEmpty() }
    .joinToString("; ")

/**
 * Encode cookie value using the specified [encoding]
 */
public fun encodeCookieValue(value: String, encoding: CookieEncoding): String = when (encoding) {
    CookieEncoding.RAW -> when {
        value.any { it.shouldEscapeInCookies() } ->
            throw IllegalArgumentException(
                "The cookie value contains characters that cannot be encoded in RAW format. " +
                    " Consider URL_ENCODING mode"
            )
        else -> value
    }
    CookieEncoding.DQUOTES -> when {
        value.contains('"') -> throw IllegalArgumentException(
            "The cookie value contains characters that cannot be encoded in DQUOTES format. " +
                "Consider URL_ENCODING mode"
        )
        value.any { it.shouldEscapeInCookies() } -> "\"$value\""
        else -> value
    }
    CookieEncoding.BASE64_ENCODING -> value.encodeBase64()
    CookieEncoding.URI_ENCODING -> value.encodeURLQueryComponent(encodeFull = true, spaceToPlus = true)
}

/**
 * Decode cookie value using the specified [encoding]
 */
public fun decodeCookieValue(encodedValue: String, encoding: CookieEncoding): String = when (encoding) {
    CookieEncoding.RAW, CookieEncoding.DQUOTES -> when {
        encodedValue.trimStart().startsWith("\"") && encodedValue.trimEnd().endsWith("\"") ->
            encodedValue.trim().removeSurrounding("\"")
        else -> encodedValue
    }
    CookieEncoding.URI_ENCODING -> encodedValue.decodeURLQueryComponent(plusIsSpace = true)
    CookieEncoding.BASE64_ENCODING -> encodedValue.decodeBase64String()
}

private fun String.assertCookieName() = when {
    any { it.shouldEscapeInCookies() } -> throw IllegalArgumentException("Cookie name is not valid: $this")
    else -> this
}

@SharedImmutable
private val cookieCharsShouldBeEscaped = setOf(';', ',', '"')

private fun Char.shouldEscapeInCookies() = isWhitespace() || this < ' ' || this in cookieCharsShouldBeEscaped

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

private fun String.toIntClamping(): Int = toLong().coerceIn(0L, Int.MAX_VALUE.toLong()).toInt()
