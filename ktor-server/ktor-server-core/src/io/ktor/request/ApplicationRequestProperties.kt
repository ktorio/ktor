package io.ktor.request

import io.ktor.features.*
import io.ktor.http.*
import kotlinx.io.charsets.*

fun ApplicationRequest.header(name: String): String? = headers[name]
fun ApplicationRequest.queryString(): String = origin.uri.substringAfter('?', "")
fun ApplicationRequest.contentType(): ContentType = header(HttpHeaders.ContentType)?.let { ContentType.parse(it) } ?: ContentType.Any
fun ApplicationRequest.contentCharset(): Charset? = contentType().charset()
fun ApplicationRequest.document(): String = path().substringAfterLast('/')
fun ApplicationRequest.path(): String = origin.uri.substringBefore('?')
fun ApplicationRequest.authorization(): String? = header(HttpHeaders.Authorization)
fun ApplicationRequest.location(): String? = header(HttpHeaders.Location)
fun ApplicationRequest.accept(): String? = header(HttpHeaders.Accept)
fun ApplicationRequest.acceptItems(): List<HeaderValue> = parseAndSortContentTypeHeader(header(HttpHeaders.Accept))
fun ApplicationRequest.acceptEncoding(): String? = header(HttpHeaders.AcceptEncoding)
fun ApplicationRequest.acceptEncodingItems(): List<HeaderValue> = parseAndSortHeader(header(HttpHeaders.AcceptEncoding))
fun ApplicationRequest.acceptLanguage(): String? = header(HttpHeaders.AcceptLanguage)
fun ApplicationRequest.acceptLanguageItems(): List<HeaderValue> = parseAndSortHeader(header(HttpHeaders.AcceptLanguage))
fun ApplicationRequest.acceptCharset(): String? = header(HttpHeaders.AcceptCharset)
fun ApplicationRequest.acceptCharsetItems(): List<HeaderValue> = parseAndSortHeader(header(HttpHeaders.AcceptCharset))
fun ApplicationRequest.isChunked(): Boolean = header(HttpHeaders.TransferEncoding)?.compareTo("chunked", ignoreCase = true) == 0
fun ApplicationRequest.isMultipart(): Boolean = contentType().match(ContentType.MultiPart.Any)
fun ApplicationRequest.userAgent(): String? = header(HttpHeaders.UserAgent)
fun ApplicationRequest.cacheControl(): String? = header(HttpHeaders.CacheControl)
fun ApplicationRequest.host(): String? = header(HttpHeaders.Host)?.substringBefore(':')
fun ApplicationRequest.port(): Int = header(HttpHeaders.Host)?.substringAfter(':', "80")?.toInt() ?: 80
fun ApplicationRequest.ranges() = header(HttpHeaders.Range)?.let { rangesSpec -> parseRangesSpecifier(rangesSpec) }

val ApplicationRequest.uri: String get() = origin.uri

/**
 * Returns request HTTP method possibly overridden via header X-Http-Method-Override
 */
val ApplicationRequest.httpMethod: HttpMethod get() = origin.method
val ApplicationRequest.httpVersion: String get() = origin.version
