/*
* Copyright 2014-2021 JetBrains s.r.o and contributors. Use of this source code is governed by the Apache 2.0 license.
*/

@file:Suppress("unused")

package io.ktor.server.request

import io.ktor.http.*
import io.ktor.server.application.*
import io.ktor.server.plugins.*
import io.ktor.utils.io.charsets.*

/**
 * Gets the first value of a [name] header or returns `null` if missing.
 */
public fun RequestProperties.header(name: String): String? = headers[name]

/**
 * Gets a request's query string or returns an empty string if missing.
 */
public fun RequestProperties.queryString(): String = origin.uri.substringAfter('?', "")

/**
 * Gets a request's content type or returns `ContentType.Any`.
 */
public fun RequestProperties.contentType(): ContentType =
    header(HttpHeaders.ContentType)?.let { ContentType.parse(it) } ?: ContentType.Any

/**
 * Gets a request's `Content-Length` header value.
 */
public fun RequestProperties.contentLength(): Long? =
    header(HttpHeaders.ContentLength)?.toLongOrNull()

/**
 * Gets a request's charset.
 */
public fun RequestProperties.contentCharset(): Charset? = contentType().charset()

/**
 * A document name is a substring after the last slash but before a query string.
 */
public fun RequestProperties.document(): String = path().substringAfterLast('/')

/**
 * Get a request's URL path without a query string.
 */
public fun RequestProperties.path(): String = origin.uri.substringBefore('?')

/**
 * Get a request's `Authorization` header value.
 */
public fun RequestProperties.authorization(): String? = header(HttpHeaders.Authorization)

/**
 * Get a request's `Location` header value.
 */
public fun RequestProperties.location(): String? = header(HttpHeaders.Location)

/**
 * Get a request's `Accept` header value.
 */
public fun RequestProperties.accept(): String? = header(HttpHeaders.Accept)

/**
 * Gets the `Accept` header content types sorted according to their qualities.
 */
public fun RequestProperties.acceptItems(): List<HeaderValue> =
    parseAndSortContentTypeHeader(header(HttpHeaders.Accept))

/**
 * Gets a request's `Accept-Encoding` header value.
 */
public fun RequestProperties.acceptEncoding(): String? = header(HttpHeaders.AcceptEncoding)

/**
 * Gets the `Accept-Encoding` header encoding types sorted according to their qualities.
 */
public fun RequestProperties.acceptEncodingItems(): List<HeaderValue> =
    parseAndSortHeader(header(HttpHeaders.AcceptEncoding))

/**
 * Gets a request's `Accept-Language` header value.
 */
public fun RequestProperties.acceptLanguage(): String? = header(HttpHeaders.AcceptLanguage)

/**
 * Gets the `Accept-Language` header languages sorted according to their qualities.
 */
public fun RequestProperties.acceptLanguageItems(): List<HeaderValue> =
    parseAndSortHeader(header(HttpHeaders.AcceptLanguage))

/**
 * Gets a request's `Accept-Charset` header value.
 */
public fun RequestProperties.acceptCharset(): String? = header(HttpHeaders.AcceptCharset)

/**
 * Gets the `Accept-Charset` header charsets sorted according to their qualities.
 */
public fun RequestProperties.acceptCharsetItems(): List<HeaderValue> =
    parseAndSortHeader(header(HttpHeaders.AcceptCharset))

/**
 * Checks whether a request's body is chunk-encoded.
 */
public fun RequestProperties.isChunked(): Boolean =
    header(HttpHeaders.TransferEncoding)?.compareTo("chunked", ignoreCase = true) == 0

/**
 * Checks whether a request body is multipart-encoded.
 */
public fun RequestProperties.isMultipart(): Boolean = contentType().match(ContentType.MultiPart.Any)

/**
 * Gets a request's `User-Agent` header value.
 */
public fun RequestProperties.userAgent(): String? = header(HttpHeaders.UserAgent)

/**
 * Gets a request's `Cache-Control` header value.
 */
public fun RequestProperties.cacheControl(): String? = header(HttpHeaders.CacheControl)

/**
 * Gets a request's host value without a port.
 * @see [port]
 */
public fun RequestProperties.host(): String = origin.serverHost

/**
 * Gets a request's port extracted from the `Host` header value.
 * @see [host]
 */
public fun RequestProperties.port(): Int = origin.serverPort

/**
 * Gets ranges parsed from a request's `Range` header value.
 */
public fun RequestProperties.ranges(): RangesSpecifier? =
    header(HttpHeaders.Range)?.let { rangesSpec -> parseRangesSpecifier(rangesSpec) }

/**
 * Gets a request's URI, including a query string.
 */
public val RequestProperties.uri: String get() = origin.uri

/**
 * Gets a request HTTP method possibly overridden using the `X-Http-Method-Override` header.
 */
public val RequestProperties.httpMethod: HttpMethod get() = origin.method

/**
 * Gets a request's HTTP version.
 */
public val RequestProperties.httpVersion: String get() = origin.version
