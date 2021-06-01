/*
* Copyright 2014-2021 JetBrains s.r.o and contributors. Use of this source code is governed by the Apache 2.0 license.
*/

@file:Suppress("unused")

package io.ktor.routing

import io.ktor.features.*
import io.ktor.http.*
import io.ktor.request.*
import io.ktor.utils.io.charsets.*

/**
 * Represents request and connection parameters possibly overridden via https headers.
 * By default it fallbacks to [ApplicationRequest.local]
 */
public val RoutingRequest.origin: RequestConnectionPoint
    get() = call.request.origin

/**
 * First header value for header with [name] or `null` if missing
 */
public fun RoutingRequest.header(name: String): String? = call.request.header(name)

/**
 * Request's query string or empty string if missing
 */
public fun RoutingRequest.queryString(): String = call.request.queryString()

/**
 * Request's content type or `ContentType.Any`
 */
public fun RoutingRequest.contentType(): ContentType = call.request.contentType()

/**
 * Request's charset
 */
public fun RoutingRequest.contentCharset(): Charset? = call.request.contentCharset()

/**
 * Request's document name (substring after the last slash but before query string)
 */
public fun RoutingRequest.document(): String = call.request.document()

/**
 * Request's path without query string
 */
public fun RoutingRequest.path(): String = call.request.path()

/**
 * Request authorization header value
 */
public fun RoutingRequest.authorization(): String? = call.request.authorization()

/**
 * Request's `Location` header value
 */
public fun RoutingRequest.location(): String? = call.request.location()

/**
 * Request's `Accept` header value
 */
public fun RoutingRequest.accept(): String? = call.request.accept()

/**
 * Parsed request's `Accept` header and sorted according to quality
 */
public fun RoutingRequest.acceptItems(): List<HeaderValue> = call.request.acceptItems()

/**
 * Request's `Accept-Encoding` header value
 */
public fun RoutingRequest.acceptEncoding(): String? = call.request.acceptEncoding()

/**
 * Parsed and sorted request's `Accept-Encoding` header value
 */
public fun RoutingRequest.acceptEncodingItems(): List<HeaderValue> = call.request.acceptEncodingItems()

/**
 * Request's `Accept-Language` header value
 */
public fun RoutingRequest.acceptLanguage(): String? = call.request.acceptLanguage()

/**
 * Parsed and sorted request's `Accept-Language` header value
 */
public fun RoutingRequest.acceptLanguageItems(): List<HeaderValue> = call.request.acceptLanguageItems()

/**
 * Request's `Accept-Charset` header value
 */
public fun RoutingRequest.acceptCharset(): String? = call.request.acceptCharset()

/**
 * Parsed and sorted request's `Accept-Charset` header value
 */
public fun RoutingRequest.acceptCharsetItems(): List<HeaderValue> = call.request.acceptCharsetItems()

/**
 * Check if request's body is chunk-encoded
 */
public fun RoutingRequest.isChunked(): Boolean = call.request.isChunked()

/**
 * Check if request body is multipart-encoded
 */
public fun RoutingRequest.isMultipart(): Boolean = call.request.isMultipart()

/**
 * Request's `User-Agent` header value
 */
public fun RoutingRequest.userAgent(): String? = call.request.userAgent()

/**
 * Request's `Cache-Control` header value
 */
public fun RoutingRequest.cacheControl(): String? = call.request.cacheControl()

/**
 * Request's host without port
 */
public fun RoutingRequest.host(): String = call.request.host()

/**
 * Request's port extracted from `Host` header value
 */
public fun RoutingRequest.port(): Int = call.request.port()

/**
 * Parsed request's `Range` header value
 */
public fun RoutingRequest.ranges(): RangesSpecifier? = call.request.ranges()

/**
 * Request's URI (including query string)
 */
public val RoutingRequest.uri: String get() = call.request.uri

/**
 * Returns request HTTP method possibly overridden via header X-Http-Method-Override
 */
public val RoutingRequest.httpMethod: HttpMethod get() = call.request.httpMethod

/**
 * Request's HTTP version
 */
public val RoutingRequest.httpVersion: String get() = call.request.httpVersion
