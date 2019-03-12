@file:Suppress("unused")

package io.ktor.http

import kotlinx.io.charsets.*

/**
 * Set `Content-Type` header.
 */
fun HttpMessageBuilder.contentType(type: ContentType): Unit = headers.set(HttpHeaders.ContentType, type.toString())

@Deprecated(
    "Content-Length is controlled by underlying engine. Don't specify it explicitly.",
    level = DeprecationLevel.ERROR
)
@Suppress("KDocMissingDocumentation", "unused", "PublicApiImplicitType", "DeprecatedCallableAddReplaceWith")
fun HttpMessageBuilder.contentLength(length: Int) = headers.set(HttpHeaders.ContentLength, length.toString())

@Deprecated("Use content with particular content type and charset instead", level = DeprecationLevel.ERROR)
@Suppress("KDocMissingDocumentation", "unused", "PublicApiImplicitType", "DeprecatedCallableAddReplaceWith")
fun HttpMessageBuilder.charset(charset: Charset) = contentType()?.let { contentType(it.withCharset(charset)) }

/**
 * Append `Max-Age` header value.
 */
fun HttpMessageBuilder.maxAge(seconds: Int): Unit = headers.append(HttpHeaders.CacheControl, "max-age:$seconds")

/**
 * Set `If-None-Match` header value.
 */
fun HttpMessageBuilder.ifNoneMatch(value: String): Unit = headers.set(HttpHeaders.IfNoneMatch, value)

/**
 * Set `User-Agent` header value.
 */
fun HttpMessageBuilder.userAgent(content: String): Unit = headers.set(HttpHeaders.UserAgent, content)

/**
 * Parse `Content-Type` header value.
 */
fun HttpMessageBuilder.contentType(): ContentType? = headers[HttpHeaders.ContentType]?.let { ContentType.parse(it) }

/**
 * Parse charset from `Content-Type` header value.
 */
fun HttpMessageBuilder.charset(): Charset? = contentType()?.charset()

/**
 * Parse `E-Tag` header value.
 */
fun HttpMessageBuilder.etag(): String? = headers[HttpHeaders.ETag]

/**
 * Parse `Vary` header value.
 */
fun HttpMessageBuilder.vary(): List<String>? = headers[HttpHeaders.Vary]?.split(",")?.map { it.trim() }

/**
 * Parse `Content-Length` header value.
 */
fun HttpMessageBuilder.contentLength(): Long? = headers[HttpHeaders.ContentLength]?.toLong()

/**
 * Parse `Content-Type` header value.
 */
fun HttpMessage.contentType(): ContentType? = headers[HttpHeaders.ContentType]?.let { ContentType.parse(it) }

/**
 * Parse charset from `Content-Type` header value.
 */
fun HttpMessage.charset(): Charset? = contentType()?.charset()

/**
 * Parse `E-Tag` header value.
 */
fun HttpMessage.etag(): String? = headers[HttpHeaders.ETag]

/**
 * Parse `Vary` header value.
 */
fun HttpMessage.vary(): List<String>? = headers[HttpHeaders.Vary]?.split(",")?.map { it.trim() }

/**
 * Parse `Content-Length` header value.
 */
fun HttpMessage.contentLength(): Long? = headers[HttpHeaders.ContentLength]?.toLong()

/**
 * Parse `Set-Cookie` header value.
 */
fun HttpMessage.setCookie(): List<Cookie> =
    headers.getAll(HttpHeaders.SetCookie)?.map { parseServerSetCookieHeader(it) } ?: emptyList()

/**
 * Parse `Set-Cookie` header value.
 */
fun HttpMessageBuilder.cookies(): List<Cookie> =
    headers.getAll(HttpHeaders.SetCookie)?.map { parseServerSetCookieHeader(it) } ?: emptyList()

/**
 * Parse `CacheControl` header.
 */
fun HttpMessage.cacheControl() = headers[HttpHeaders.CacheControl]?.let {
    parseHeaderValue(it)
} ?: emptyList()
