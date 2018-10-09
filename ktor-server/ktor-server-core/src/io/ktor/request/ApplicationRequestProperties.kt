@file:Suppress("unused")

package io.ktor.request

import io.ktor.features.*
import io.ktor.http.*
import io.ktor.util.*
import kotlinx.io.charsets.*

/**
 * First header value for header with [name] or `null` if missing
 */
fun ApplicationRequest.header(name: String): String? = headers[name]

/**
 * Request's query string or empty string if missing
 */
fun ApplicationRequest.queryString(): String = origin.uri.substringAfter('?', "")

/**
 * Request's content type or `ContentType.Any`
 */
fun ApplicationRequest.contentType(): ContentType = header(HttpHeaders.ContentType)?.let { ContentType.parse(it) } ?: ContentType.Any

/**
 * Request's charset
 */
fun ApplicationRequest.contentCharset(): Charset? = contentType().charset()

/**
 * Request's document name (substring after the last slash but before query string)
 */
fun ApplicationRequest.document(): String = path().substringAfterLast('/')

/**
 * Request's path without query string
 */
fun ApplicationRequest.path(): String = origin.uri.substringBefore('?')

/**
 * Request authorization header value
 */
@KtorExperimentalAPI
fun ApplicationRequest.authorization(): String? = header(HttpHeaders.Authorization)

/**
 * Request's `Location` header value
 */
fun ApplicationRequest.location(): String? = header(HttpHeaders.Location)

/**
 * Request's `Accept` header value
 */
fun ApplicationRequest.accept(): String? = header(HttpHeaders.Accept)

/**
 * Parsed request's `Accept` header and sorted according to quality
 */
fun ApplicationRequest.acceptItems(): List<HeaderValue> = parseAndSortContentTypeHeader(header(HttpHeaders.Accept))

/**
 * Request's `Accept-Encoding` header value
 */
fun ApplicationRequest.acceptEncoding(): String? = header(HttpHeaders.AcceptEncoding)

/**
 * Parsed and sorted request's `Accept-Encoding` header value
 */
fun ApplicationRequest.acceptEncodingItems(): List<HeaderValue> = parseAndSortHeader(header(HttpHeaders.AcceptEncoding))

/**
 * Request's `Accept-Language` header value
 */
fun ApplicationRequest.acceptLanguage(): String? = header(HttpHeaders.AcceptLanguage)

/**
 * Parsed and sorted request's `Accept-Language` header value
 */
fun ApplicationRequest.acceptLanguageItems(): List<HeaderValue> = parseAndSortHeader(header(HttpHeaders.AcceptLanguage))

/**
 * Request's `Accept-Charset` header value
 */
fun ApplicationRequest.acceptCharset(): String? = header(HttpHeaders.AcceptCharset)
/**
 * Parsed and sorted request's `Accept-Charset` header value
 */
fun ApplicationRequest.acceptCharsetItems(): List<HeaderValue> = parseAndSortHeader(header(HttpHeaders.AcceptCharset))

/**
 * Check if request's body is chunk-encoded
 */
fun ApplicationRequest.isChunked(): Boolean = header(HttpHeaders.TransferEncoding)?.compareTo("chunked", ignoreCase = true) == 0

/**
 * Check if request body is multipart-encoded
 */
fun ApplicationRequest.isMultipart(): Boolean = contentType().match(ContentType.MultiPart.Any)

/**
 * Request's `User-Agent` header value
 */
fun ApplicationRequest.userAgent(): String? = header(HttpHeaders.UserAgent)

/**
 * Request's `Cache-Control` header value
 */
fun ApplicationRequest.cacheControl(): String? = header(HttpHeaders.CacheControl)

/**
 * Request's host without port
 */
fun ApplicationRequest.host(): String? = header(HttpHeaders.Host)?.substringBefore(':')

/**
 * Request's port extracted from `Host` header value
 */
fun ApplicationRequest.port(): Int = header(HttpHeaders.Host)?.substringAfter(':', "80")?.toInt() ?: 80

/**
 * Parsed request's `Range` header value
 */
fun ApplicationRequest.ranges(): RangesSpecifier? = header(HttpHeaders.Range)?.let { rangesSpec -> parseRangesSpecifier(rangesSpec) }

/**
 * Request's URI (including query string)
 */
val ApplicationRequest.uri: String get() = origin.uri

/**
 * Returns request HTTP method possibly overridden via header X-Http-Method-Override
 */
val ApplicationRequest.httpMethod: HttpMethod get() = origin.method

/**
 * Request's HTTP version
 */
val ApplicationRequest.httpVersion: String get() = origin.version
