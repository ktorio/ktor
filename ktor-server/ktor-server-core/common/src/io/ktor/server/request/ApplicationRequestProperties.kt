/*
 * Copyright 2014-2024 JetBrains s.r.o and contributors. Use of this source code is governed by the Apache 2.0 license.
 */

@file:Suppress("unused")

package io.ktor.server.request

import io.ktor.http.*
import io.ktor.server.plugins.*
import io.ktor.utils.io.charsets.*

/**
 * Gets the first value of a [name] header or returns `null` if missing.
 *
 * [Report a problem](https://ktor.io/feedback/?fqname=io.ktor.server.request.header)
 */
public fun ApplicationRequest.header(name: String): String? = headers[name]

/**
 * Gets a request's query string or returns an empty string if missing.
 *
 * [Report a problem](https://ktor.io/feedback/?fqname=io.ktor.server.request.queryString)
 */
public fun ApplicationRequest.queryString(): String = origin.uri.substringAfter('?', "")

/**
 * Gets a request's content type or returns `ContentType.Any`.
 *
 * [Report a problem](https://ktor.io/feedback/?fqname=io.ktor.server.request.contentType)
 */
public fun ApplicationRequest.contentType(): ContentType =
    header(HttpHeaders.ContentType)?.let { ContentType.parse(it) } ?: ContentType.Any

/**
 * Gets a request's `Content-Length` header value.
 *
 * [Report a problem](https://ktor.io/feedback/?fqname=io.ktor.server.request.contentLength)
 */
public fun ApplicationRequest.contentLength(): Long? =
    header(HttpHeaders.ContentLength)?.toLongOrNull()

/**
 * Gets a request's charset.
 *
 * [Report a problem](https://ktor.io/feedback/?fqname=io.ktor.server.request.contentCharset)
 */
public fun ApplicationRequest.contentCharset(): Charset? = contentType().charset()

/**
 * A document name is a substring after the last slash but before a query string.
 *
 * [Report a problem](https://ktor.io/feedback/?fqname=io.ktor.server.request.document)
 */
public fun ApplicationRequest.document(): String = path().substringAfterLast('/')

/**
 * Get a request's URL path without a query string.
 *
 * [Report a problem](https://ktor.io/feedback/?fqname=io.ktor.server.request.path)
 */
public fun ApplicationRequest.path(): String = origin.uri.substringBefore('?')

/**
 * Get a request's `Authorization` header value.
 *
 * [Report a problem](https://ktor.io/feedback/?fqname=io.ktor.server.request.authorization)
 */
public fun ApplicationRequest.authorization(): String? = header(HttpHeaders.Authorization)

/**
 * Get a request's `Location` header value.
 *
 * [Report a problem](https://ktor.io/feedback/?fqname=io.ktor.server.request.location)
 */
public fun ApplicationRequest.location(): String? = header(HttpHeaders.Location)

/**
 * Get a request's `Accept` header value.
 *
 * [Report a problem](https://ktor.io/feedback/?fqname=io.ktor.server.request.accept)
 */
public fun ApplicationRequest.accept(): String? = header(HttpHeaders.Accept)

/**
 * Gets the `Accept` header content types sorted according to their qualities.
 *
 * [Report a problem](https://ktor.io/feedback/?fqname=io.ktor.server.request.acceptItems)
 */
public fun ApplicationRequest.acceptItems(): List<HeaderValue> =
    parseAndSortContentTypeHeader(header(HttpHeaders.Accept))

/**
 * Gets a request's `Accept-Encoding` header value.
 *
 * [Report a problem](https://ktor.io/feedback/?fqname=io.ktor.server.request.acceptEncoding)
 */
public fun ApplicationRequest.acceptEncoding(): String? = header(HttpHeaders.AcceptEncoding)

/**
 * Gets the `Accept-Encoding` header encoding types sorted according to their qualities.
 *
 * [Report a problem](https://ktor.io/feedback/?fqname=io.ktor.server.request.acceptEncodingItems)
 */
public fun ApplicationRequest.acceptEncodingItems(): List<HeaderValue> =
    parseAndSortHeader(header(HttpHeaders.AcceptEncoding))

/**
 * Gets a request's `Accept-Language` header value.
 *
 * [Report a problem](https://ktor.io/feedback/?fqname=io.ktor.server.request.acceptLanguage)
 */
public fun ApplicationRequest.acceptLanguage(): String? = header(HttpHeaders.AcceptLanguage)

/**
 * Gets the `Accept-Language` header languages sorted according to their qualities.
 *
 * [Report a problem](https://ktor.io/feedback/?fqname=io.ktor.server.request.acceptLanguageItems)
 */
public fun ApplicationRequest.acceptLanguageItems(): List<HeaderValue> =
    parseAndSortHeader(header(HttpHeaders.AcceptLanguage))

/**
 * Gets a request's `Accept-Charset` header value.
 *
 * [Report a problem](https://ktor.io/feedback/?fqname=io.ktor.server.request.acceptCharset)
 */
public fun ApplicationRequest.acceptCharset(): String? = header(HttpHeaders.AcceptCharset)

/**
 * Gets the `Accept-Charset` header charsets sorted according to their qualities.
 *
 * [Report a problem](https://ktor.io/feedback/?fqname=io.ktor.server.request.acceptCharsetItems)
 */
public fun ApplicationRequest.acceptCharsetItems(): List<HeaderValue> =
    parseAndSortHeader(header(HttpHeaders.AcceptCharset))

/**
 * Checks whether a request's body is chunk-encoded.
 *
 * [Report a problem](https://ktor.io/feedback/?fqname=io.ktor.server.request.isChunked)
 */
public fun ApplicationRequest.isChunked(): Boolean =
    header(HttpHeaders.TransferEncoding)?.compareTo("chunked", ignoreCase = true) == 0

/**
 * Checks whether a request body is multipart-encoded.
 *
 * [Report a problem](https://ktor.io/feedback/?fqname=io.ktor.server.request.isMultipart)
 */
public fun ApplicationRequest.isMultipart(): Boolean = contentType() in ContentType.MultiPart

/**
 * Gets a request's `User-Agent` header value.
 *
 * [Report a problem](https://ktor.io/feedback/?fqname=io.ktor.server.request.userAgent)
 */
public fun ApplicationRequest.userAgent(): String? = header(HttpHeaders.UserAgent)

/**
 * Gets a request's `Cache-Control` header value.
 *
 * [Report a problem](https://ktor.io/feedback/?fqname=io.ktor.server.request.cacheControl)
 */
public fun ApplicationRequest.cacheControl(): String? = header(HttpHeaders.CacheControl)

/**
 * Gets a request's host value without a port.
 *
 * [Report a problem](https://ktor.io/feedback/?fqname=io.ktor.server.request.host)
 *
 * @see [port]
 */
public fun ApplicationRequest.host(): String = origin.serverHost

/**
 * Gets a request's port extracted from the `Host` header value.
 *
 * [Report a problem](https://ktor.io/feedback/?fqname=io.ktor.server.request.port)
 *
 * @see [host]
 */
public fun ApplicationRequest.port(): Int = origin.serverPort

/**
 * Gets ranges parsed from a request's `Range` header value.
 *
 * [Report a problem](https://ktor.io/feedback/?fqname=io.ktor.server.request.ranges)
 */
public fun ApplicationRequest.ranges(): RangesSpecifier? =
    header(HttpHeaders.Range)?.let { rangesSpec -> parseRangesSpecifier(rangesSpec) }

/**
 * Gets a request's URI, including a query string.
 *
 * [Report a problem](https://ktor.io/feedback/?fqname=io.ktor.server.request.uri)
 */
public val ApplicationRequest.uri: String get() = origin.uri

/**
 * Gets a request HTTP method possibly overridden using the `X-Http-Method-Override` header.
 *
 * [Report a problem](https://ktor.io/feedback/?fqname=io.ktor.server.request.httpMethod)
 */
public val ApplicationRequest.httpMethod: HttpMethod get() = origin.method

/**
 * Gets a request's HTTP version.
 *
 * [Report a problem](https://ktor.io/feedback/?fqname=io.ktor.server.request.httpVersion)
 */
public val ApplicationRequest.httpVersion: String get() = origin.version
