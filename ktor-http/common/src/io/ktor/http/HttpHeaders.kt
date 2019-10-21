/*
 * Copyright 2014-2019 JetBrains s.r.o and contributors. Use of this source code is governed by the Apache 2.0 license.
 */

package io.ktor.http

import io.ktor.util.*

@Suppress("unused", "KDocMissingDocumentation", "PublicApiImplicitType", "MayBeConstant")
object HttpHeaders {
    // Permanently registered standard HTTP headers
    // The list is taken from http://www.iana.org/assignments/message-headers/message-headers.xml#perm-headers

    val Accept = "Accept"
    val AcceptCharset = "Accept-Charset"
    val AcceptEncoding = "Accept-Encoding"
    val AcceptLanguage = "Accept-Language"
    val AcceptRanges = "Accept-Ranges"
    val Age = "Age"
    val Allow = "Allow"
    val ALPN = "ALPN" // Application-Layer Protocol Negotiation, HTTP/2
    val AuthenticationInfo = "Authentication-Info"
    val Authorization = "Authorization"
    val CacheControl = "Cache-Control"
    val Connection = "Connection"
    val ContentDisposition = "Content-Disposition"
    val ContentEncoding = "Content-Encoding"
    val ContentLanguage = "Content-Language"
    val ContentLength = "Content-Length"
    val ContentLocation = "Content-Location"
    val ContentRange = "Content-Range"
    val ContentType = "Content-Type"
    val Cookie = "Cookie"
    val DASL = "DASL" // WebDAV Search
    val Date = "Date"
    val DAV = "DAV" // WebDAV
    val Depth = "Depth" // WebDAV
    val Destination = "Destination"
    val ETag = "ETag"
    val Expect = "Expect"
    val Expires = "Expires"
    val From = "From"
    val Forwarded = "Forwarded"
    val Host = "Host"
    val HTTP2Settings = "HTTP2-Settings"
    val If = "If"
    val IfMatch = "If-Match"
    val IfModifiedSince = "If-Modified-Since"
    val IfNoneMatch = "If-None-Match"
    val IfRange = "If-Range"
    val IfScheduleTagMatch = "If-Schedule-Tag-Match"
    val IfUnmodifiedSince = "If-Unmodified-Since"
    val LastModified = "Last-Modified"
    val Location = "Location"
    val LockToken = "Lock-Token"
    val Link = "Link"
    val MaxForwards = "Max-Forwards"
    val MIMEVersion = "MIME-Version"
    val OrderingType = "Ordering-Type"
    val Origin = "Origin"
    val Overwrite = "Overwrite"
    val Position = "Position"
    val Pragma = "Pragma"
    val Prefer = "Prefer"
    val PreferenceApplied = "Preference-Applied"
    val ProxyAuthenticate = "Proxy-Authenticate"
    val ProxyAuthenticationInfo = "Proxy-Authentication-Info"
    val ProxyAuthorization = "Proxy-Authorization"
    val PublicKeyPins = "Public-Key-Pins"
    val PublicKeyPinsReportOnly = "Public-Key-Pins-Report-Only"
    val Range = "Range"
    val Referrer = "Referer"
    val RetryAfter = "Retry-After"
    val ScheduleReply = "Schedule-Reply"
    val ScheduleTag = "Schedule-Tag"
    val SecWebSocketAccept = "Sec-WebSocket-Accept"
    val SecWebSocketExtensions = "Sec-WebSocket-Extensions"
    val SecWebSocketKey = "Sec-WebSocket-Key"
    val SecWebSocketProtocol = "Sec-WebSocket-Protocol"
    val SecWebSocketVersion = "Sec-WebSocket-Version"
    val Server = "Server"
    val SetCookie = "Set-Cookie"
    val SLUG = "SLUG" // Atom Publishing
    val StrictTransportSecurity = "Strict-Transport-Security"
    val TE = "TE"
    val Timeout = "Timeout"
    val Trailer = "Trailer"
    val TransferEncoding = "Transfer-Encoding"
    val Upgrade = "Upgrade"
    val UserAgent = "User-Agent"
    val Vary = "Vary"
    val Via = "Via"
    val Warning = "Warning"
    val WWWAuthenticate = "WWW-Authenticate"

    // CORS
    val AccessControlAllowOrigin = "Access-Control-Allow-Origin"
    val AccessControlAllowMethods = "Access-Control-Allow-Methods"
    val AccessControlAllowCredentials = "Access-Control-Allow-Credentials"
    val AccessControlAllowHeaders = "Access-Control-Allow-Headers"

    val AccessControlRequestMethod = "Access-Control-Request-Method"
    val AccessControlRequestHeaders = "Access-Control-Request-Headers"
    val AccessControlExposeHeaders = "Access-Control-Expose-Headers"
    val AccessControlMaxAge = "Access-Control-Max-Age"

    // Unofficial de-facto headers
    val XHttpMethodOverride = "X-Http-Method-Override"
    val XForwardedHost = "X-Forwarded-Host"
    val XForwardedServer = "X-Forwarded-Server"
    val XForwardedProto = "X-Forwarded-Proto"
    val XForwardedFor = "X-Forwarded-For"

    val XRequestId = "X-Request-ID"
    val XCorrelationId = "X-Correlation-ID"
    val XTotalCount = "X-Total-Count"

    /**
     * Check if [header] is unsafe. Header is unsafe if listed in [UnsafeHeadersList]
     */
    fun isUnsafe(header: String): Boolean = UnsafeHeadersArray.any { it.equals(header, ignoreCase = true) }

    private val UnsafeHeadersArray: Array<String> = arrayOf(ContentLength, ContentType, TransferEncoding, Upgrade)

    @Deprecated("Use UnsafeHeadersList instead.", replaceWith = ReplaceWith("HttpHeaders.UnsafeHeadersList"))
    val UnsafeHeaders: Array<String> get() = UnsafeHeadersArray.copyOf()

    /**
     * A list of header names that are not safe to use unless it is low-level engine implementation.
     */
    val UnsafeHeadersList: List<String> = UnsafeHeadersArray.asList()

    /**
     * Validates header [name] throwing [IllegalHeaderNameException] when the name is not valid.
     */
    @KtorExperimentalAPI
    fun checkHeaderName(name: String) {
        name.forEachIndexed { index, ch ->
            if (ch <= ' ' || isDelimiter(ch)) {
                throw IllegalHeaderNameException(name, index)
            }
        }
    }

    /**
     * Validates header [value] throwing [IllegalHeaderValueException] when the value is not valid.
     */
    @KtorExperimentalAPI
    fun checkHeaderValue(value: String) {
        value.forEachIndexed { index, ch ->
            if (ch == ' ' || ch == '\u0009') return@forEachIndexed
            if (ch < ' ') {
                throw IllegalHeaderValueException(value, index)
            }
        }
    }
}

/**
 * Thrown when an attempt to set unsafe header detected. A header is unsafe if listed in [HttpHeaders.UnsafeHeadersList].
 */
class UnsafeHeaderException(header: String) : IllegalArgumentException(
    "Header $header is controlled by the engine and " +
        "cannot be set explicitly"
)

/**
 * Thrown when an illegal header name was used.
 * A header name should only consist from visible characters
 * without delimiters "double quote" and the following characters: `(),/:;<=>?@[\]{}`.
 * @property headerName that was tried to use
 * @property position at which validation failed
 */
@KtorExperimentalAPI
class IllegalHeaderNameException(val headerName: String, val position: Int) : IllegalArgumentException(
    "Header name '$headerName' contains illegal character '${headerName[position]}'" +
        " (code ${(headerName[position].toInt() and 0xff)})"
)

/**
 * Thrown when an illegal header value was used.
 * A header value should only consist from visible characters, spaces and/or HTAB (0x09).
 * @property headerValue that was tried to use
 * @property position at which validation failed
 */
@KtorExperimentalAPI
class IllegalHeaderValueException(val headerValue: String, val position: Int) : IllegalArgumentException(
    "Header value '$headerValue' contains illegal character '${headerValue[position]}'" +
        " (code ${(headerValue[position].toInt() and 0xff)})"
)

private fun isDelimiter(ch: Char): Boolean = ch in "\"(),/:;<=>?@[\\]{}"
