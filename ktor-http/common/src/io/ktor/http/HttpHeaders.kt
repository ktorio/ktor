/*
 * Copyright 2014-2026 JetBrains s.r.o and contributors. Use of this source code is governed by the Apache 2.0 license.
 */

package io.ktor.http

import io.ktor.http.HttpHeaders.UnsafeHeadersList

@Suppress(
    "unused",
    "KDocMissingDocumentation",
    "PublicApiImplicitType",
    "ConstPropertyName",
)
public object HttpHeaders {
    // Permanently registered standard HTTP headers
    // The list is taken from https://www.iana.org/assignments/message-headers/message-headers.xml#perm-headers

    public const val Accept: String = "Accept"
    @Deprecated("The Accept-Charset request header has been deprecated in RFC 9110. " +
        "UTF-8 should be used by default in modern applications.")
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

    @Deprecated("Use Accept constant instead.", ReplaceWith("Accept"), DeprecationLevel.ERROR)
    public fun getAccept(): String = Accept

    @Suppress("DEPRECATION")
    @Deprecated("Use AcceptCharset constant instead.", ReplaceWith("AcceptCharset"), DeprecationLevel.ERROR)
    public fun getAcceptCharset(): String = AcceptCharset

    @Deprecated("Use AcceptEncoding constant instead.", ReplaceWith("AcceptEncoding"), DeprecationLevel.ERROR)
    public fun getAcceptEncoding(): String = AcceptEncoding

    @Deprecated("Use AcceptLanguage constant instead.", ReplaceWith("AcceptLanguage"), DeprecationLevel.ERROR)
    public fun getAcceptLanguage(): String = AcceptLanguage

    @Deprecated("Use AcceptRanges constant instead.", ReplaceWith("AcceptRanges"), DeprecationLevel.ERROR)
    public fun getAcceptRanges(): String = AcceptRanges

    @Deprecated("Use Age constant instead.", ReplaceWith("Age"), DeprecationLevel.ERROR)
    public fun getAge(): String = Age

    @Deprecated("Use Allow constant instead.", ReplaceWith("Allow"), DeprecationLevel.ERROR)
    public fun getAllow(): String = Allow

    @Deprecated("Use ALPN constant instead.", ReplaceWith("ALPN"), DeprecationLevel.ERROR)
    public fun getALPN(): String = ALPN

    @Deprecated("Use AuthenticationInfo constant instead.", ReplaceWith("AuthenticationInfo"), DeprecationLevel.ERROR)
    public fun getAuthenticationInfo(): String = AuthenticationInfo

    @Deprecated("Use Authorization constant instead.", ReplaceWith("Authorization"), DeprecationLevel.ERROR)
    public fun getAuthorization(): String = Authorization

    @Deprecated("Use CacheControl constant instead.", ReplaceWith("CacheControl"), DeprecationLevel.ERROR)
    public fun getCacheControl(): String = CacheControl

    @Deprecated("Use Connection constant instead.", ReplaceWith("Connection"), DeprecationLevel.ERROR)
    public fun getConnection(): String = Connection

    @Deprecated("Use ContentDisposition constant instead.", ReplaceWith("ContentDisposition"), DeprecationLevel.ERROR)
    public fun getContentDisposition(): String = ContentDisposition

    @Deprecated("Use ContentEncoding constant instead.", ReplaceWith("ContentEncoding"), DeprecationLevel.ERROR)
    public fun getContentEncoding(): String = ContentEncoding

    @Deprecated("Use ContentLanguage constant instead.", ReplaceWith("ContentLanguage"), DeprecationLevel.ERROR)
    public fun getContentLanguage(): String = ContentLanguage

    @Deprecated("Use ContentLength constant instead.", ReplaceWith("ContentLength"), DeprecationLevel.ERROR)
    public fun getContentLength(): String = ContentLength

    @Deprecated("Use ContentLocation constant instead.", ReplaceWith("ContentLocation"), DeprecationLevel.ERROR)
    public fun getContentLocation(): String = ContentLocation

    @Deprecated("Use ContentRange constant instead.", ReplaceWith("ContentRange"), DeprecationLevel.ERROR)
    public fun getContentRange(): String = ContentRange

    @Deprecated("Use ContentType constant instead.", ReplaceWith("ContentType"), DeprecationLevel.ERROR)
    public fun getContentType(): String = ContentType

    @Deprecated("Use Cookie constant instead.", ReplaceWith("Cookie"), DeprecationLevel.ERROR)
    public fun getCookie(): String = Cookie

    @Deprecated("Use DASL constant instead.", ReplaceWith("DASL"), DeprecationLevel.ERROR)
    public fun getDASL(): String = DASL

    @Deprecated("Use Date constant instead.", ReplaceWith("Date"), DeprecationLevel.ERROR)
    public fun getDate(): String = Date

    @Deprecated("Use DAV constant instead.", ReplaceWith("DAV"), DeprecationLevel.ERROR)
    public fun getDAV(): String = DAV

    @Deprecated("Use Depth constant instead.", ReplaceWith("Depth"), DeprecationLevel.ERROR)
    public fun getDepth(): String = Depth

    @Deprecated("Use Destination constant instead.", ReplaceWith("Destination"), DeprecationLevel.ERROR)
    public fun getDestination(): String = Destination

    @Deprecated("Use ETag constant instead.", ReplaceWith("ETag"), DeprecationLevel.ERROR)
    public fun getETag(): String = ETag

    @Deprecated("Use Expect constant instead.", ReplaceWith("Expect"), DeprecationLevel.ERROR)
    public fun getExpect(): String = Expect

    @Deprecated("Use Expires constant instead.", ReplaceWith("Expires"), DeprecationLevel.ERROR)
    public fun getExpires(): String = Expires

    @Deprecated("Use From constant instead.", ReplaceWith("From"), DeprecationLevel.ERROR)
    public fun getFrom(): String = From

    @Deprecated("Use Forwarded constant instead.", ReplaceWith("Forwarded"), DeprecationLevel.ERROR)
    public fun getForwarded(): String = Forwarded

    @Deprecated("Use Host constant instead.", ReplaceWith("Host"), DeprecationLevel.ERROR)
    public fun getHost(): String = Host

    @Deprecated("Use HTTP2Settings constant instead.", ReplaceWith("HTTP2Settings"), DeprecationLevel.ERROR)
    public fun getHTTP2Settings(): String = HTTP2Settings

    @Deprecated("Use If constant instead.", ReplaceWith("If"), DeprecationLevel.ERROR)
    public fun getIf(): String = If

    @Deprecated("Use IfMatch constant instead.", ReplaceWith("IfMatch"), DeprecationLevel.ERROR)
    public fun getIfMatch(): String = IfMatch

    @Deprecated("Use IfModifiedSince constant instead.", ReplaceWith("IfModifiedSince"), DeprecationLevel.ERROR)
    public fun getIfModifiedSince(): String = IfModifiedSince

    @Deprecated("Use IfNoneMatch constant instead.", ReplaceWith("IfNoneMatch"), DeprecationLevel.ERROR)
    public fun getIfNoneMatch(): String = IfNoneMatch

    @Deprecated("Use IfRange constant instead.", ReplaceWith("IfRange"), DeprecationLevel.ERROR)
    public fun getIfRange(): String = IfRange

    @Deprecated("Use IfScheduleTagMatch constant instead.", ReplaceWith("IfScheduleTagMatch"), DeprecationLevel.ERROR)
    public fun getIfScheduleTagMatch(): String = IfScheduleTagMatch

    @Deprecated("Use IfUnmodifiedSince constant instead.", ReplaceWith("IfUnmodifiedSince"), DeprecationLevel.ERROR)
    public fun getIfUnmodifiedSince(): String = IfUnmodifiedSince

    @Deprecated("Use LastModified constant instead.", ReplaceWith("LastModified"), DeprecationLevel.ERROR)
    public fun getLastModified(): String = LastModified

    @Deprecated("Use Location constant instead.", ReplaceWith("Location"), DeprecationLevel.ERROR)
    public fun getLocation(): String = Location

    @Deprecated("Use LockToken constant instead.", ReplaceWith("LockToken"), DeprecationLevel.ERROR)
    public fun getLockToken(): String = LockToken

    @Deprecated("Use Link constant instead.", ReplaceWith("Link"), DeprecationLevel.ERROR)
    public fun getLink(): String = Link

    @Deprecated("Use MaxForwards constant instead.", ReplaceWith("MaxForwards"), DeprecationLevel.ERROR)
    public fun getMaxForwards(): String = MaxForwards

    @Deprecated("Use MIMEVersion constant instead.", ReplaceWith("MIMEVersion"), DeprecationLevel.ERROR)
    public fun getMIMEVersion(): String = MIMEVersion

    @Deprecated("Use OrderingType constant instead.", ReplaceWith("OrderingType"), DeprecationLevel.ERROR)
    public fun getOrderingType(): String = OrderingType

    @Deprecated("Use Origin constant instead.", ReplaceWith("Origin"), DeprecationLevel.ERROR)
    public fun getOrigin(): String = Origin

    @Deprecated("Use Overwrite constant instead.", ReplaceWith("Overwrite"), DeprecationLevel.ERROR)
    public fun getOverwrite(): String = Overwrite

    @Deprecated("Use Position constant instead.", ReplaceWith("Position"), DeprecationLevel.ERROR)
    public fun getPosition(): String = Position

    @Deprecated("Use Pragma constant instead.", ReplaceWith("Pragma"), DeprecationLevel.ERROR)
    public fun getPragma(): String = Pragma

    @Deprecated("Use Prefer constant instead.", ReplaceWith("Prefer"), DeprecationLevel.ERROR)
    public fun getPrefer(): String = Prefer

    @Deprecated("Use PreferenceApplied constant instead.", ReplaceWith("PreferenceApplied"), DeprecationLevel.ERROR)
    public fun getPreferenceApplied(): String = PreferenceApplied

    @Deprecated("Use ProxyAuthenticate constant instead.", ReplaceWith("ProxyAuthenticate"), DeprecationLevel.ERROR)
    public fun getProxyAuthenticate(): String = ProxyAuthenticate

    @Deprecated(
        "Use ProxyAuthenticationInfo constant instead.",
        ReplaceWith("ProxyAuthenticationInfo"),
        DeprecationLevel.ERROR
    )
    public fun getProxyAuthenticationInfo(): String = ProxyAuthenticationInfo

    @Deprecated("Use ProxyAuthorization constant instead.", ReplaceWith("ProxyAuthorization"), DeprecationLevel.ERROR)
    public fun getProxyAuthorization(): String = ProxyAuthorization

    @Deprecated("Use PublicKeyPins constant instead.", ReplaceWith("PublicKeyPins"), DeprecationLevel.ERROR)
    public fun getPublicKeyPins(): String = PublicKeyPins

    @Deprecated(
        "Use PublicKeyPinsReportOnly constant instead.",
        ReplaceWith("PublicKeyPinsReportOnly"),
        DeprecationLevel.ERROR
    )
    public fun getPublicKeyPinsReportOnly(): String = PublicKeyPinsReportOnly

    @Deprecated("Use Range constant instead.", ReplaceWith("Range"), DeprecationLevel.ERROR)
    public fun getRange(): String = Range

    @Deprecated("Use Referrer constant instead.", ReplaceWith("Referrer"), DeprecationLevel.ERROR)
    public fun getReferrer(): String = Referrer

    @Deprecated("Use RetryAfter constant instead.", ReplaceWith("RetryAfter"), DeprecationLevel.ERROR)
    public fun getRetryAfter(): String = RetryAfter

    @Deprecated("Use ScheduleReply constant instead.", ReplaceWith("ScheduleReply"), DeprecationLevel.ERROR)
    public fun getScheduleReply(): String = ScheduleReply

    @Deprecated("Use ScheduleTag constant instead.", ReplaceWith("ScheduleTag"), DeprecationLevel.ERROR)
    public fun getScheduleTag(): String = ScheduleTag

    @Deprecated("Use SecWebSocketAccept constant instead.", ReplaceWith("SecWebSocketAccept"), DeprecationLevel.ERROR)
    public fun getSecWebSocketAccept(): String = SecWebSocketAccept

    @Deprecated(
        "Use SecWebSocketExtensions constant instead.",
        ReplaceWith("SecWebSocketExtensions"),
        DeprecationLevel.ERROR
    )
    public fun getSecWebSocketExtensions(): String = SecWebSocketExtensions

    @Deprecated("Use SecWebSocketKey constant instead.", ReplaceWith("SecWebSocketKey"), DeprecationLevel.ERROR)
    public fun getSecWebSocketKey(): String = SecWebSocketKey

    @Deprecated(
        "Use SecWebSocketProtocol constant instead.",
        ReplaceWith("SecWebSocketProtocol"),
        DeprecationLevel.ERROR
    )
    public fun getSecWebSocketProtocol(): String = SecWebSocketProtocol

    @Deprecated("Use SecWebSocketVersion constant instead.", ReplaceWith("SecWebSocketVersion"), DeprecationLevel.ERROR)
    public fun getSecWebSocketVersion(): String = SecWebSocketVersion

    @Deprecated("Use Server constant instead.", ReplaceWith("Server"), DeprecationLevel.ERROR)
    public fun getServer(): String = Server

    @Deprecated("Use SetCookie constant instead.", ReplaceWith("SetCookie"), DeprecationLevel.ERROR)
    public fun getSetCookie(): String = SetCookie

    @Deprecated("Use SLUG constant instead.", ReplaceWith("SLUG"), DeprecationLevel.ERROR)
    public fun getSLUG(): String = SLUG

    @Deprecated(
        "Use StrictTransportSecurity constant instead.",
        ReplaceWith("StrictTransportSecurity"),
        DeprecationLevel.ERROR
    )
    public fun getStrictTransportSecurity(): String = StrictTransportSecurity

    @Deprecated("Use TE constant instead.", ReplaceWith("TE"), DeprecationLevel.ERROR)
    public fun getTE(): String = TE

    @Deprecated("Use Timeout constant instead.", ReplaceWith("Timeout"), DeprecationLevel.ERROR)
    public fun getTimeout(): String = Timeout

    @Deprecated("Use Trailer constant instead.", ReplaceWith("Trailer"), DeprecationLevel.ERROR)
    public fun getTrailer(): String = Trailer

    @Deprecated("Use TransferEncoding constant instead.", ReplaceWith("TransferEncoding"), DeprecationLevel.ERROR)
    public fun getTransferEncoding(): String = TransferEncoding

    @Deprecated("Use Upgrade constant instead.", ReplaceWith("Upgrade"), DeprecationLevel.ERROR)
    public fun getUpgrade(): String = Upgrade

    @Deprecated("Use UserAgent constant instead.", ReplaceWith("UserAgent"), DeprecationLevel.ERROR)
    public fun getUserAgent(): String = UserAgent

    @Deprecated("Use Vary constant instead.", ReplaceWith("Vary"), DeprecationLevel.ERROR)
    public fun getVary(): String = Vary

    @Deprecated("Use Via constant instead.", ReplaceWith("Via"), DeprecationLevel.ERROR)
    public fun getVia(): String = Via

    @Deprecated("Use Warning constant instead.", ReplaceWith("Warning"), DeprecationLevel.ERROR)
    public fun getWarning(): String = Warning

    @Deprecated("Use WWWAuthenticate constant instead.", ReplaceWith("WWWAuthenticate"), DeprecationLevel.ERROR)
    public fun getWWWAuthenticate(): String = WWWAuthenticate

    @Deprecated(
        "Use AccessControlAllowOrigin constant instead.",
        ReplaceWith("AccessControlAllowOrigin"),
        DeprecationLevel.ERROR
    )
    public fun getAccessControlAllowOrigin(): String = AccessControlAllowOrigin

    @Deprecated(
        "Use AccessControlAllowMethods constant instead.",
        ReplaceWith("AccessControlAllowMethods"),
        DeprecationLevel.ERROR
    )
    public fun getAccessControlAllowMethods(): String = AccessControlAllowMethods

    @Deprecated(
        "Use AccessControlAllowCredentials constant instead.",
        ReplaceWith("AccessControlAllowCredentials"),
        DeprecationLevel.ERROR
    )
    public fun getAccessControlAllowCredentials(): String = AccessControlAllowCredentials

    @Deprecated(
        "Use AccessControlAllowHeaders constant instead.",
        ReplaceWith("AccessControlAllowHeaders"),
        DeprecationLevel.ERROR
    )
    public fun getAccessControlAllowHeaders(): String = AccessControlAllowHeaders

    @Deprecated(
        "Use AccessControlRequestMethod constant instead.",
        ReplaceWith("AccessControlRequestMethod"),
        DeprecationLevel.ERROR
    )
    public fun getAccessControlRequestMethod(): String = AccessControlRequestMethod

    @Deprecated(
        "Use AccessControlRequestHeaders constant instead.",
        ReplaceWith("AccessControlRequestHeaders"),
        DeprecationLevel.ERROR
    )
    public fun getAccessControlRequestHeaders(): String = AccessControlRequestHeaders

    @Deprecated(
        "Use AccessControlExposeHeaders constant instead.",
        ReplaceWith("AccessControlExposeHeaders"),
        DeprecationLevel.ERROR
    )
    public fun getAccessControlExposeHeaders(): String = AccessControlExposeHeaders

    @Deprecated("Use AccessControlMaxAge constant instead.", ReplaceWith("AccessControlMaxAge"), DeprecationLevel.ERROR)
    public fun getAccessControlMaxAge(): String = AccessControlMaxAge

    @Deprecated("Use XHttpMethodOverride constant instead.", ReplaceWith("XHttpMethodOverride"), DeprecationLevel.ERROR)
    public fun getXHttpMethodOverride(): String = XHttpMethodOverride

    @Deprecated("Use XForwardedHost constant instead.", ReplaceWith("XForwardedHost"), DeprecationLevel.ERROR)
    public fun getXForwardedHost(): String = XForwardedHost

    @Deprecated("Use XForwardedServer constant instead.", ReplaceWith("XForwardedServer"), DeprecationLevel.ERROR)
    public fun getXForwardedServer(): String = XForwardedServer

    @Deprecated("Use XForwardedProto constant instead.", ReplaceWith("XForwardedProto"), DeprecationLevel.ERROR)
    public fun getXForwardedProto(): String = XForwardedProto

    @Deprecated("Use XForwardedFor constant instead.", ReplaceWith("XForwardedFor"), DeprecationLevel.ERROR)
    public fun getXForwardedFor(): String = XForwardedFor

    @Deprecated("Use XForwardedPort constant instead.", ReplaceWith("XForwardedPort"), DeprecationLevel.ERROR)
    public fun getXForwardedPort(): String = XForwardedPort

    @Deprecated("Use XRequestId constant instead.", ReplaceWith("XRequestId"), DeprecationLevel.ERROR)
    public fun getXRequestId(): String = XRequestId

    @Deprecated("Use XCorrelationId constant instead.", ReplaceWith("XCorrelationId"), DeprecationLevel.ERROR)
    public fun getXCorrelationId(): String = XCorrelationId

    @Deprecated("Use XTotalCount constant instead.", ReplaceWith("XTotalCount"), DeprecationLevel.ERROR)
    public fun getXTotalCount(): String = XTotalCount

    @Deprecated("Use LastEventID constant instead.", ReplaceWith("LastEventID"), DeprecationLevel.ERROR)
    public fun getLastEventID(): String = LastEventID
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
