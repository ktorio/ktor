package io.ktor.http

import kotlinx.io.charsets.*


fun HttpMessageBuilder.contentType(type: ContentType) = headers.set(HttpHeaders.ContentType, type.toString())
fun HttpMessageBuilder.contentLength(length: Int) = headers.set(HttpHeaders.ContentLength, length.toString())
fun HttpMessageBuilder.charset(charset: Charset) = contentType()?.let { contentType(it.withCharset(charset)) }
fun HttpMessageBuilder.maxAge(seconds: Int) = headers.append(HttpHeaders.CacheControl, "max-age:$seconds")
fun HttpMessageBuilder.ifNoneMatch(value: String) = headers.set(HttpHeaders.IfNoneMatch, value)
fun HttpMessageBuilder.userAgent(content: String) = headers.set(HttpHeaders.UserAgent, content)

fun HttpMessageBuilder.contentType(): ContentType? = headers[HttpHeaders.ContentType]?.let { ContentType.parse(it) }
fun HttpMessageBuilder.charset(): Charset? = contentType()?.charset()
fun HttpMessageBuilder.etag(): String? = headers[HttpHeaders.ETag]
fun HttpMessageBuilder.vary(): List<String>? = headers[HttpHeaders.Vary]?.split(",")?.map { it.trim() }
fun HttpMessageBuilder.contentLength(): Int? = headers[HttpHeaders.ContentLength]?.toInt()

fun HttpMessage.contentType(): ContentType? = headers[HttpHeaders.ContentType]?.let { ContentType.parse(it) }
fun HttpMessage.charset(): Charset? = contentType()?.charset()
fun HttpMessage.etag(): String? = headers[HttpHeaders.ETag]
fun HttpMessage.vary(): List<String>? = headers[HttpHeaders.Vary]?.split(",")?.map { it.trim() }
fun HttpMessage.contentLength(): Int? = headers[HttpHeaders.ContentLength]?.toInt()
fun HttpMessage.setCookie(): List<Cookie> = headers.getAll(HttpHeaders.SetCookie)?.map { parseServerSetCookieHeader(it) } ?: emptyList()

fun HttpMessageBuilder.cookies(): List<Cookie> =
        headers.getAll(HttpHeaders.SetCookie)?.map { parseServerSetCookieHeader(it) } ?: emptyList()
