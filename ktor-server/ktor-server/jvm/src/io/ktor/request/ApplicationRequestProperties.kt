/*
* Copyright 2014-2021 JetBrains s.r.o and contributors. Use of this source code is governed by the Apache 2.0 license.
*/

@file:Suppress("unused")

package io.ktor.request

import io.ktor.features.*
import io.ktor.http.*
import io.ktor.utils.io.charsets.*

/**
 * First header value for header with [name] or `null` if missing
 */
public fun ApplicationRequest.header(name: String): String? = headers[name]

/**
 * Request's query string or empty string if missing
 */
public fun ApplicationRequest.queryString(): String = origin.uri.substringAfter('?', "")

/**
 * Request's content type or `ContentType.Any`
 */
public fun ApplicationRequest.contentType(): ContentType =
    header(HttpHeaders.ContentType)?.let { ContentType.parse(it) } ?: ContentType.Any

/**
 * Request's charset
 */
public fun ApplicationRequest.contentCharset(): Charset? = contentType().charset()

/**
 * Request's document name (substring after the last slash but before query string)
 */
public fun ApplicationRequest.document(): String = path().substringAfterLast('/')

/**
 * Request's path without query string
 */
public fun ApplicationRequest.path(): String = origin.uri.substringBefore('?')

/**
 * Request authorization header value
 */
public fun ApplicationRequest.authorization(): String? = header(HttpHeaders.Authorization)

/**
 * Request's `Location` header value
 */
public fun ApplicationRequest.location(): String? = header(HttpHeaders.Location)

/**
 * Request's `Accept` header value
 */
public fun ApplicationRequest.accept(): String? = header(HttpHeaders.Accept)

/**
 * Parsed request's `Accept` header and sorted according to quality
 */
public fun ApplicationRequest.acceptItems(): List<HeaderValue> =
    parseAndSortContentTypeHeader(header(HttpHeaders.Accept))

/**
 * Request's `Accept-Encoding` header value
 */
public fun ApplicationRequest.acceptEncoding(): String? = header(HttpHeaders.AcceptEncoding)

/**
 * Parsed and sorted request's `Accept-Encoding` header value
 */
public fun ApplicationRequest.acceptEncodingItems(): List<HeaderValue> =
    parseAndSortHeader(header(HttpHeaders.AcceptEncoding))

/**
 * Request's `Accept-Language` header value
 */
public fun ApplicationRequest.acceptLanguage(): String? = header(HttpHeaders.AcceptLanguage)

/**
 * Parsed and sorted request's `Accept-Language` header value
 */
public fun ApplicationRequest.acceptLanguageItems(): List<HeaderValue> =
    parseAndSortHeader(header(HttpHeaders.AcceptLanguage))

/**
 * Request's `Accept-Charset` header value
 */
public fun ApplicationRequest.acceptCharset(): String? = header(HttpHeaders.AcceptCharset)

/**
 * Parsed and sorted request's `Accept-Charset` header value
 */
public fun ApplicationRequest.acceptCharsetItems(): List<HeaderValue> =
    parseAndSortHeader(header(HttpHeaders.AcceptCharset))

/**
 * Check if request's body is chunk-encoded
 */
public fun ApplicationRequest.isChunked(): Boolean =
    header(HttpHeaders.TransferEncoding)?.compareTo("chunked", ignoreCase = true) == 0

/**
 * Check if request body is multipart-encoded
 */
public fun ApplicationRequest.isMultipart(): Boolean = contentType().match(ContentType.MultiPart.Any)

/**
 * Request's `User-Agent` header value
 */
public fun ApplicationRequest.userAgent(): String? = header(HttpHeaders.UserAgent)

/**
 * Request's `Cache-Control` header value
 */
public fun ApplicationRequest.cacheControl(): String? = header(HttpHeaders.CacheControl)

/**
 * Request's host without port
 */
public fun ApplicationRequest.host(): String = origin.host

/**
 * Request's port extracted from `Host` header value
 */
public fun ApplicationRequest.port(): Int = origin.port

/**
 * Parsed request's `Range` header value
 */
public fun ApplicationRequest.ranges(): RangesSpecifier? =
    header(HttpHeaders.Range)?.let { rangesSpec -> parseRangesSpecifier(rangesSpec) }

/**
 * Request's URI (including query string)
 */
public val ApplicationRequest.uri: String get() = origin.uri

/**
 * Returns request HTTP method possibly overridden via header X-Http-Method-Override
 */
public val ApplicationRequest.httpMethod: HttpMethod get() = origin.method

/**
 * Request's HTTP version
 */
public val ApplicationRequest.httpVersion: String get() = origin.version
