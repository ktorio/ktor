package io.ktor.client.utils

import io.ktor.http.*
import io.ktor.util.*
import java.nio.charset.*
import java.text.*
import java.util.*


typealias Headers = ValuesMap

typealias HeadersBuilder = ValuesMapBuilder

private val HTTP_DATE_FORMAT: SimpleDateFormat
    get() = SimpleDateFormat("EEE, dd MMM yyyy HH:mm:ss z", Locale.US).apply {
        timeZone = TimeZone.getTimeZone("GMT")
    }

private fun parseHttpDate(date: String): Date = HTTP_DATE_FORMAT.parse(date)

private fun formatHttpDate(date: Date): String = HTTP_DATE_FORMAT.format(date)

fun HttpMessageBuilder.contentType(type: ContentType) = headers.set(HttpHeaders.ContentType, type.toString())
fun HttpMessageBuilder.contentLength(length: Int) = headers.set(HttpHeaders.ContentLength, length.toString())
fun HttpMessageBuilder.charset(charset: Charset) = contentType()?.let { contentType(it.withCharset(charset)) }
fun HttpMessageBuilder.maxAge(seconds: Int) = headers.append(HttpHeaders.CacheControl, "max-age:$seconds")
fun HttpMessageBuilder.ifModifiedSince(date: Date) = headers.set(HttpHeaders.IfModifiedSince, formatHttpDate(date))
fun HttpMessageBuilder.ifNoneMatch(value: String) = headers.set(HttpHeaders.IfNoneMatch, value)
fun HttpMessageBuilder.userAgent(content: String) = headers.set(HttpHeaders.UserAgent, content)

fun HttpMessageBuilder.contentType(): ContentType? = headers[HttpHeaders.ContentType]?.let { ContentType.parse(it) }
fun HttpMessageBuilder.charset(): Charset? = contentType()?.charset()
fun HttpMessageBuilder.lastModified(): Date? = headers[HttpHeaders.LastModified]?.let { parseHttpDate(it) }
fun HttpMessageBuilder.etag(): String? = headers[HttpHeaders.ETag]
fun HttpMessageBuilder.expires(): Date? = headers[HttpHeaders.Expires]?.let { parseHttpDate(it) }
fun HttpMessageBuilder.vary(): List<String>? = headers[HttpHeaders.Vary]?.split(",")?.map { it.trim() }
fun HttpMessageBuilder.contentLength(): Int? = headers[HttpHeaders.ContentLength]?.toInt()

fun HttpMessage.contentType(): ContentType? = headers[HttpHeaders.ContentType]?.let { ContentType.parse(it) }
fun HttpMessage.charset(): Charset? = contentType()?.charset()
fun HttpMessage.lastModified(): Date? = headers[HttpHeaders.LastModified]?.let { parseHttpDate(it) }
fun HttpMessage.etag(): String? = headers[HttpHeaders.ETag]
fun HttpMessage.expires(): Date? = headers[HttpHeaders.Expires]?.let { parseHttpDate(it) }
fun HttpMessage.vary(): List<String>? = headers[HttpHeaders.Vary]?.split(",")?.map { it.trim() }
fun HttpMessage.contentLength(): Int? = headers[HttpHeaders.ContentLength]?.toInt()

fun HttpMessageBuilder.cookies(): List<Cookie> =
        headers.getAll(HttpHeaders.SetCookie)?.map { parseServerSetCookieHeader(it) } ?: listOf()