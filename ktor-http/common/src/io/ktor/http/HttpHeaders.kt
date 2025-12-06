/*
* Copyright 2014-2021 JetBrains s.r.o and contributors. Use of this source code is governed by the Apache 2.0 license.
*/

package io.ktor.http

@Suppress("unused", "KDocMissingDocumentation", "PublicApiImplicitType")
public object HttpHeaders {
    // Permanently registered standard HTTP headers
    // The list is taken from http://www.iana.org/assignments/message-headers/message-headers.xml#perm-headers

    public const val Accept: String = "Accept"
    public const val AcceptCharset: String = "Accept-Charset"
    public const val AcceptEncoding: String = "Accept-Encoding"
    public const val AcceptLanguage: String = "Accept-Language"
    public const val AcceptRanges: String = "Accept-Ranges"
    public const val Age: String = "Age"
    public const val Allow: String = "Allow"

    // Application-Layer Protocol Negotiation, HTTP/2
    public const val ALPN: String = "ALPN"
    public const val AuthenticationInfo: String = "Authentication-Info"
    public const val Authorization: String = "Authorization"
    public const val CacheControl: String = "Cache-Control"
    public const val Connection: String = "Connection"
    public const val ContentDisposition: String = "Content-Disposition"
    public const val ContentEncoding: String = "Content-Encoding"
    public const val ContentLanguage: String = "Content-Language"
    public const val ContentLength: String = "Content-Length"
    public const val ContentLocation: String = "Content-Location"
    public const val ContentRange: String = "Content-Range"
    public const val ContentType: String = "Content-Type"
    public const val Cookie: String = "Cookie"

    // WebDAV Search
    public const val DASL: String = "DASL"
    public const val Date: String = "Date"

    // WebDAV
    public const val DAV: String = "DAV"
    public const val Depth: String = "Depth"

    public const val Destination: String = "Destination"
    public const val ETag: String = "ETag"
    public const val Expect: String = "Expect"
    public const val Expires: String = "Expires"
    public const val From: String = "From"
    public const val Forwarded: String = "Forwarded"
    public const val Host: String = "Host"
    public const val HTTP2Settings: String = "HTTP2-Settings"
    public const val If: String = "If"
    public const val IfMatch: String = "If-Match"
    public const val IfModifiedSince: String = "If-Modified-Since"
    public const val IfNoneMatch: String = "If-None-Match"
    public const val IfRange: String = "If-Range"
    public const val IfScheduleTagMatch: String = "If-Schedule-Tag-Match"
    public const val IfUnmodifiedSince: String = "If-Unmodified-Since"
    public const val LastModified: String = "Last-Modified"
    public const val Location: String = "Location"
    public const val LockToken: String = "Lock-Token"
    public const val Link: String = "Link"
    public const val MaxForwards: String = "Max-Forwards"
    public const val MIMEVersion: String = "MIME-Version"
    public const val OrderingType: String = "Ordering-Type"
    public const val Origin: String = "Origin"
    public const val Overwrite: String = "Overwrite"
    public const val Position: String = "Position"
    public const val Pragma: String = "Pragma"
    public const val Prefer: String = "Prefer"
    public const val PreferenceApplied: String = "Preference-Applied"
    public const val ProxyAuthenticate: String = "Proxy-Authenticate"
    public const val ProxyAuthenticationInfo: String = "Proxy-Authentication-Info"
    public const val ProxyAuthorization: String = "Proxy-Authorization"
    public const val PublicKeyPins: String = "Public-Key-Pins"
    public const val PublicKeyPinsReportOnly: String = "Public-Key-Pins-Report-Only"
    public const val Range: String = "Range"
    public const val Referrer: String = "Referer"
    public const val RetryAfter: String = "Retry-After"
    public const val ScheduleReply: String = "Schedule-Reply"
    public const val ScheduleTag: String = "Schedule-Tag"
    public const val SecWebSocketAccept: String = "Sec-WebSocket-Accept"
    public const val SecWebSocketExtensions: String = "Sec-WebSocket-Extensions"
    public const val SecWebSocketKey: String = "Sec-WebSocket-Key"
    public const val SecWebSocketProtocol: String = "Sec-WebSocket-Protocol"
    public const val SecWebSocketVersion: String = "Sec-WebSocket-Version"
    public const val Server: String = "Server"
    public const val SetCookie: String = "Set-Cookie"

    // Atom Publishing
    public const val SLUG: String = "SLUG"
    public const val StrictTransportSecurity: String = "Strict-Transport-Security"
    public const val TE: String = "TE"
    public const val Timeout: String = "Timeout"
    public const val Trailer: String = "Trailer"
    public const val TransferEncoding: String = "Transfer-Encoding"
    public const val Upgrade: String = "Upgrade"
    public const val UserAgent: String = "User-Agent"
    public const val Vary: String = "Vary"
    public const val Via: String = "Via"
    public const val Warning: String = "Warning"
    public const val WWWAuthenticate: String = "WWW-Authenticate"

    // CORS
    public const val AccessControlAllowOrigin: String = "Access-Control-Allow-Origin"
    public const val AccessControlAllowMethods: String = "Access-Control-Allow-Methods"
    public const val AccessControlAllowCredentials: String = "Access-Control-Allow-Credentials"
    public const val AccessControlAllowHeaders: String = "Access-Control-Allow-Headers"

    public const val AccessControlRequestMethod: String = "Access-Control-Request-Method"
    public const val AccessControlRequestHeaders: String = "Access-Control-Request-Headers"
    public const val AccessControlExposeHeaders: String = "Access-Control-Expose-Headers"
    public const val AccessControlMaxAge: String = "Access-Control-Max-Age"

    // Unofficial de-facto headers
    public const val XHttpMethodOverride: String = "X-Http-Method-Override"
    public const val XForwardedHost: String = "X-Forwarded-Host"
    public const val XForwardedServer: String = "X-Forwarded-Server"
    public const val XForwardedProto: String = "X-Forwarded-Proto"
    public const val XForwardedFor: String = "X-Forwarded-For"

    public const val XForwardedPort: String = "X-Forwarded-Port"

    public const val XRequestId: String = "X-Request-ID"
    public const val XCorrelationId: String = "X-Correlation-ID"
    public const val XTotalCount: String = "X-Total-Count"

    public const val LastEventID: String = "Last-Event-ID"

    /**
     * Check if [header] is unsafe. Header is unsafe if listed in [UnsafeHeadersList]
     *
     * [Report a problem](https://ktor.io/feedback/?fqname=io.ktor.http.HttpHeaders.isUnsafe)
     */
    public fun isUnsafe(header: String): Boolean = UnsafeHeadersArray.any { it.equals(header, ignoreCase = true) }

    private val UnsafeHeadersArray: Array<String> = arrayOf(TransferEncoding, Upgrade)

    @Deprecated(
        "Use UnsafeHeadersList instead.",
        replaceWith = ReplaceWith("HttpHeaders.UnsafeHeadersList"),
        level = DeprecationLevel.ERROR
    )
    public val UnsafeHeaders: Array<String>
        get() = UnsafeHeadersArray.copyOf()

    /**
     * A list of header names that are not safe to use unless it is low-level engine implementation.
     *
     * [Report a problem](https://ktor.io/feedback/?fqname=io.ktor.http.HttpHeaders.UnsafeHeadersList)
     */
    public val UnsafeHeadersList: List<String> = UnsafeHeadersArray.asList()

    /**
     * Validates header [name] throwing [IllegalHeaderNameException] when the name is not valid.
     *
     * [Report a problem](https://ktor.io/feedback/?fqname=io.ktor.http.HttpHeaders.checkHeaderName)
     */
    public fun checkHeaderName(name: String) {
        name.forEachIndexed { index, ch ->
            if (ch <= ' ' || isDelimiter(ch)) {
                throw IllegalHeaderNameException(name, index)
            }
        }
    }

    /**
     * Validates header [value] throwing [IllegalHeaderValueException] when the value is not valid.
     *
     * [Report a problem](https://ktor.io/feedback/?fqname=io.ktor.http.HttpHeaders.checkHeaderValue)
     */
    public fun checkHeaderValue(value: String) {
        value.forEachIndexed { index, ch ->
            if (ch < ' ' && ch != '\u0009') {
                throw IllegalHeaderValueException(value, index)
            }
        }
    }
}

/**
 * Thrown when an attempt to set unsafe header detected. A header is unsafe if listed in [HttpHeaders.UnsafeHeadersList].
 *
 * [Report a problem](https://ktor.io/feedback/?fqname=io.ktor.http.UnsafeHeaderException)
 */
public class UnsafeHeaderException(header: String) : IllegalArgumentException(
    "Header(s) $header are controlled by the engine and " +
        "cannot be set explicitly"
)

/**
 * Thrown when an illegal header name was used.
 * A header name should only consist from visible characters
 * without delimiters "double quote" and the following characters: `(),/:;<=>?@[\]{}`.
 *
 * [Report a problem](https://ktor.io/feedback/?fqname=io.ktor.http.IllegalHeaderNameException)
 *
 * @property headerName that was tried to use
 * @property position at which validation failed
 */
public class IllegalHeaderNameException(public val headerName: String, public val position: Int) :
    IllegalArgumentException(
        "Header name '$headerName' contains illegal character '${headerName[position]}'" +
            " (code ${(headerName[position].code and 0xff)})"
    )

/**
 * Thrown when an illegal header value was used.
 * A header value should only consist from visible characters, spaces and/or HTAB (0x09).
 *
 * [Report a problem](https://ktor.io/feedback/?fqname=io.ktor.http.IllegalHeaderValueException)
 *
 * @property headerValue that was tried to use
 * @property position at which validation failed
 */
public class IllegalHeaderValueException(public val headerValue: String, public val position: Int) :
    IllegalArgumentException(
        "Header value '$headerValue' contains illegal character '${headerValue[position]}'" +
            " (code ${(headerValue[position].code and 0xff)})"
    )

private fun isDelimiter(ch: Char): Boolean = ch in "\"(),/:;<=>?@[\\]{}"
