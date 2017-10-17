package io.ktor.client.utils

import io.ktor.client.request.HttpRequestBuilder
import io.ktor.client.response.HttpResponse
import io.ktor.client.response.HttpResponseBuilder
import io.ktor.http.ContentType
import io.ktor.http.HttpHeaders
import io.ktor.http.charset
import io.ktor.util.ValuesMap
import io.ktor.util.ValuesMapBuilder
import java.nio.charset.Charset
import java.text.SimpleDateFormat
import java.util.*

typealias Headers = ValuesMap

typealias HeadersBuilder = ValuesMapBuilder

val HTTP_DATE_FORMAT: SimpleDateFormat get() = SimpleDateFormat("EEE, dd MMM yyyy HH:mm:ss z", Locale.US).apply {
        timeZone = TimeZone.getTimeZone("GMT")
    }

fun HeadersBuilder.charset(): Charset? = get(HttpHeaders.ContentType)?.let { ContentType.parse(it).charset() }
fun HeadersBuilder.userAgent(content: String) = set(HttpHeaders.UserAgent, content)

fun HttpRequestBuilder.contentType(type: ContentType) = headers.set(HttpHeaders.ContentType, type.toString())
fun HttpRequestBuilder.maxAge(): Int? = cacheControl.maxAge
fun HttpRequestBuilder.ifModifiedSince(date: Date) =
        headers.set(HttpHeaders.IfModifiedSince, HTTP_DATE_FORMAT.format(date))
fun HttpRequestBuilder.ifNoneMatch(value: String) = headers.set(HttpHeaders.IfNoneMatch, value)
fun HttpRequestBuilder.maxAge(seconds: Int) = headers.append(HttpHeaders.CacheControl, "max-age:$seconds")

fun HttpResponse.lastModified(): Date? = headers[HttpHeaders.LastModified]?.let { HTTP_DATE_FORMAT.parse(it) }
fun HttpResponse.etag(): String? = headers[HttpHeaders.ETag]
fun HttpResponse.expires(): Date? = headers[HttpHeaders.Expires]?.let { HTTP_DATE_FORMAT.parse(it) }
fun HttpResponse.vary(): List<String>? = headers[HttpHeaders.Vary]?.split(",")?.map { it.trim() }

fun HttpResponseBuilder.expires(): Date? = headers[HttpHeaders.Expires]?.let { HTTP_DATE_FORMAT.parse(it) }
