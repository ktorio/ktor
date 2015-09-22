package org.jetbrains.ktor.application

import org.jetbrains.ktor.http.*
import org.jetbrains.ktor.util.*

fun ApplicationRequest.queryString(): String = requestLine.queryString()
fun ApplicationRequest.queryParameters(): ValuesMap = parseQueryString(queryString())
fun ApplicationRequest.contentType(): ContentType = header(HttpHeaders.ContentType)?.let { ContentType.parse(it) } ?: ContentType.Any
fun ApplicationRequest.document(): String = requestLine.document()
fun ApplicationRequest.path(): String = requestLine.path()
fun ApplicationRequest.authorization(): String? = header(HttpHeaders.Authorization)
fun ApplicationRequest.location(): String? = header(HttpHeaders.Location)
fun ApplicationRequest.accept(): String? = header(HttpHeaders.Accept)
fun ApplicationRequest.acceptItems(): List<HeaderItem> = header(HttpHeaders.Accept).orderedContentTypeHeaderItems()
fun ApplicationRequest.acceptEncoding(): String? = header(HttpHeaders.AcceptEncoding)
fun ApplicationRequest.acceptEncodingItems(): List<HeaderItem> = header(HttpHeaders.AcceptEncoding).orderedHeaderItems()
fun ApplicationRequest.acceptLanguage(): String? = header(HttpHeaders.AcceptLanguage)
fun ApplicationRequest.acceptLanguageItems(): List<HeaderItem> = header(HttpHeaders.AcceptLanguage).orderedHeaderItems()
fun ApplicationRequest.acceptCharset(): String? = header(HttpHeaders.AcceptCharset)
fun ApplicationRequest.acceptCharsetItems(): List<HeaderItem> = header(HttpHeaders.AcceptCharset).orderedHeaderItems()
fun ApplicationRequest.isChunked(): Boolean = header(HttpHeaders.TransferEncoding)?.compareTo("chunked", ignoreCase = true) == 0
fun ApplicationRequest.userAgent(): String? = header(HttpHeaders.UserAgent)
fun ApplicationRequest.cacheControl(): String? = header(HttpHeaders.CacheControl)
fun ApplicationRequest.host(): String? = header(HttpHeaders.Host)?.substringBefore(':')
fun ApplicationRequest.port(): Int = header(HttpHeaders.Host)?.substringAfter(':', "80")?.toInt() ?: 80
