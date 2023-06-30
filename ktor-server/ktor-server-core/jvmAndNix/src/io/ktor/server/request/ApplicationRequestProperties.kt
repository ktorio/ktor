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
public fun Request.header(name: String): String? = headers[name]

/**
 * Gets a request's query string or returns an empty string if missing.
 */
public fun Request.queryString(): String = origin.uri.substringAfter('?', "")

/**
 * Gets a request's content type or returns `ContentType.Any`.
 */
public fun Request.contentType(): ContentType =
    header(HttpHeaders.ContentType)?.let { ContentType.parse(it) } ?: ContentType.Any

/**
 * Gets a request's `Content-Length` header value.
 */
public fun Request.contentLength(): Long? =
    header(HttpHeaders.ContentLength)?.toLongOrNull()

/**
 * Gets a request's charset.
 */
public fun Request.contentCharset(): Charset? = contentType().charset()

/**
 * A document name is a substring after the last slash but before a query string.
 */
public fun Request.document(): String = path().substringAfterLast('/')

/**
 * Get a request's URL path without a query string.
 */
public fun Request.path(): String = origin.uri.substringBefore('?')

/**
 * Get a request's `Authorization` header value.
 */
public fun Request.authorization(): String? = header(HttpHeaders.Authorization)

/**
 * Get a request's `Location` header value.
 */
public fun Request.location(): String? = header(HttpHeaders.Location)

/**
 * Get a request's `Accept` header value.
 */
public fun Request.accept(): String? = header(HttpHeaders.Accept)

/**
 * Gets the `Accept` header content types sorted according to their qualities.
 */
public fun Request.acceptItems(): List<HeaderValue> =
    parseAndSortContentTypeHeader(header(HttpHeaders.Accept))

/**
 * Gets a request's `Accept-Encoding` header value.
 */
public fun Request.acceptEncoding(): String? = header(HttpHeaders.AcceptEncoding)

/**
 * Gets the `Accept-Encoding` header encoding types sorted according to their qualities.
 */
public fun Request.acceptEncodingItems(): List<HeaderValue> =
    parseAndSortHeader(header(HttpHeaders.AcceptEncoding))

/**
 * Gets a request's `Accept-Language` header value.
 */
public fun Request.acceptLanguage(): String? = header(HttpHeaders.AcceptLanguage)

/**
 * Gets the `Accept-Language` header languages sorted according to their qualities.
 */
public fun Request.acceptLanguageItems(): List<HeaderValue> =
    parseAndSortHeader(header(HttpHeaders.AcceptLanguage))

/**
 * Gets a request's `Accept-Charset` header value.
 */
public fun Request.acceptCharset(): String? = header(HttpHeaders.AcceptCharset)

/**
 * Gets the `Accept-Charset` header charsets sorted according to their qualities.
 */
public fun Request.acceptCharsetItems(): List<HeaderValue> =
    parseAndSortHeader(header(HttpHeaders.AcceptCharset))

/**
 * Checks whether a request's body is chunk-encoded.
 */
public fun Request.isChunked(): Boolean =
    header(HttpHeaders.TransferEncoding)?.compareTo("chunked", ignoreCase = true) == 0

/**
 * Checks whether a request body is multipart-encoded.
 */
public fun Request.isMultipart(): Boolean = contentType().match(ContentType.MultiPart.Any)

/**
 * Gets a request's `User-Agent` header value.
 */
public fun Request.userAgent(): String? = header(HttpHeaders.UserAgent)

/**
 * Gets a request's `Cache-Control` header value.
 */
public fun Request.cacheControl(): String? = header(HttpHeaders.CacheControl)

/**
 * Gets a request's host value without a port.
 * @see [port]
 */
public fun Request.host(): String = origin.serverHost

/**
 * Gets a request's port extracted from the `Host` header value.
 * @see [host]
 */
public fun Request.port(): Int = origin.serverPort

/**
 * Gets ranges parsed from a request's `Range` header value.
 */
public fun Request.ranges(): RangesSpecifier? =
    header(HttpHeaders.Range)?.let { rangesSpec -> parseRangesSpecifier(rangesSpec) }

/**
 * Gets a request's URI, including a query string.
 */
public val Request.uri: String get() = origin.uri

/**
 * Gets a request HTTP method possibly overridden using the `X-Http-Method-Override` header.
 */
public val Request.httpMethod: HttpMethod get() = origin.method

/**
 * Gets a request's HTTP version.
 */
public val Request.httpVersion: String get() = origin.version
