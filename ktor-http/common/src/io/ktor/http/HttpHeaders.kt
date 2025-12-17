/*
* Copyright 2014-2021 JetBrains s.r.o and contributors. Use of this source code is governed by the Apache 2.0 license.
*/

package io.ktor.http

import kotlin.jvm.JvmName

@Suppress(
    "unused",
    "KDocMissingDocumentation",
    "PublicApiImplicitType",
    "ConstPropertyName",
    "ObjectPropertyName"
)
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

    @Deprecated("Use Accept constant instead.", ReplaceWith("Accept"))
    @get:JvmName("getAccept")
    public val _Accept: String = Accept

    @Deprecated("Use AcceptCharset constant instead.", ReplaceWith("AcceptCharset"))
    @get:JvmName("getAcceptCharset")
    public val _AcceptCharset: String = AcceptCharset

    @Deprecated("Use AcceptEncoding constant instead.", ReplaceWith("AcceptEncoding"))
    @get:JvmName("getAcceptEncoding")
    public val _AcceptEncoding: String = AcceptEncoding

    @Deprecated("Use AcceptLanguage constant instead.", ReplaceWith("AcceptLanguage"))
    @get:JvmName("getAcceptLanguage")
    public val _AcceptLanguage: String = AcceptLanguage

    @Deprecated("Use AcceptRanges constant instead.", ReplaceWith("AcceptRanges"))
    @get:JvmName("getAcceptRanges")
    public val _AcceptRanges: String = AcceptRanges

    @Deprecated("Use Age constant instead.", ReplaceWith("Age"))
    @get:JvmName("getAge")
    public val _Age: String = Age

    @Deprecated("Use Allow constant instead.", ReplaceWith("Allow"))
    @get:JvmName("getAllow")
    public val _Allow: String = Allow

    @Deprecated("Use ALPN constant instead.", ReplaceWith("ALPN"))
    @get:JvmName("getALPN")
    public val _ALPN: String = ALPN

    @Deprecated("Use AuthenticationInfo constant instead.", ReplaceWith("AuthenticationInfo"))
    @get:JvmName("getAuthenticationInfo")
    public val _AuthenticationInfo: String = AuthenticationInfo

    @Deprecated("Use Authorization constant instead.", ReplaceWith("Authorization"))
    @get:JvmName("getAuthorization")
    public val _Authorization: String = Authorization

    @Deprecated("Use CacheControl constant instead.", ReplaceWith("CacheControl"))
    @get:JvmName("getCacheControl")
    public val _CacheControl: String = CacheControl

    @Deprecated("Use Connection constant instead.", ReplaceWith("Connection"))
    @get:JvmName("getConnection")
    public val _Connection: String = Connection

    @Deprecated("Use ContentDisposition constant instead.", ReplaceWith("ContentDisposition"))
    @get:JvmName("getContentDisposition")
    public val _ContentDisposition: String = ContentDisposition

    @Deprecated("Use ContentEncoding constant instead.", ReplaceWith("ContentEncoding"))
    @get:JvmName("getContentEncoding")
    public val _ContentEncoding: String = ContentEncoding

    @Deprecated("Use ContentLanguage constant instead.", ReplaceWith("ContentLanguage"))
    @get:JvmName("getContentLanguage")
    public val _ContentLanguage: String = ContentLanguage

    @Deprecated("Use ContentLength constant instead.", ReplaceWith("ContentLength"))
    @get:JvmName("getContentLength")
    public val _ContentLength: String = ContentLength

    @Deprecated("Use ContentLocation constant instead.", ReplaceWith("ContentLocation"))
    @get:JvmName("getContentLocation")
    public val _ContentLocation: String = ContentLocation

    @Deprecated("Use ContentRange constant instead.", ReplaceWith("ContentRange"))
    @get:JvmName("getContentRange")
    public val _ContentRange: String = ContentRange

    @Deprecated("Use ContentType constant instead.", ReplaceWith("ContentType"))
    @get:JvmName("getContentType")
    public val _ContentType: String = ContentType

    @Deprecated("Use Cookie constant instead.", ReplaceWith("Cookie"))
    @get:JvmName("getCookie")
    public val _Cookie: String = Cookie

    @Deprecated("Use DASL constant instead.", ReplaceWith("DASL"))
    @get:JvmName("getDASL")
    public val _DASL: String = DASL

    @Deprecated("Use Date constant instead.", ReplaceWith("Date"))
    @get:JvmName("getDate")
    public val _Date: String = Date

    @Deprecated("Use DAV constant instead.", ReplaceWith("DAV"))
    @get:JvmName("getDAV")
    public val _DAV: String = DAV

    @Deprecated("Use Depth constant instead.", ReplaceWith("Depth"))
    @get:JvmName("getDepth")
    public val _Depth: String = Depth

    @Deprecated("Use Destination constant instead.", ReplaceWith("Destination"))
    @get:JvmName("getDestination")
    public val _Destination: String = Destination

    @Deprecated("Use ETag constant instead.", ReplaceWith("ETag"))
    @get:JvmName("getETag")
    public val _ETag: String = ETag

    @Deprecated("Use Expect constant instead.", ReplaceWith("Expect"))
    @get:JvmName("getExpect")
    public val _Expect: String = Expect

    @Deprecated("Use Expires constant instead.", ReplaceWith("Expires"))
    @get:JvmName("getExpires")
    public val _Expires: String = Expires

    @Deprecated("Use From constant instead.", ReplaceWith("From"))
    @get:JvmName("getFrom")
    public val _From: String = From

    @Deprecated("Use Forwarded constant instead.", ReplaceWith("Forwarded"))
    @get:JvmName("getForwarded")
    public val _Forwarded: String = Forwarded

    @Deprecated("Use Host constant instead.", ReplaceWith("Host"))
    @get:JvmName("getHost")
    public val _Host: String = Host

    @Deprecated("Use HTTP2Settings constant instead.", ReplaceWith("HTTP2Settings"))
    @get:JvmName("getHTTP2Settings")
    public val _HTTP2Settings: String = HTTP2Settings

    @Deprecated("Use If constant instead.", ReplaceWith("If"))
    @get:JvmName("getIf")
    public val _If: String = If

    @Deprecated("Use IfMatch constant instead.", ReplaceWith("IfMatch"))
    @get:JvmName("getIfMatch")
    public val _IfMatch: String = IfMatch

    @Deprecated("Use IfModifiedSince constant instead.", ReplaceWith("IfModifiedSince"))
    @get:JvmName("getIfModifiedSince")
    public val _IfModifiedSince: String = IfModifiedSince

    @Deprecated("Use IfNoneMatch constant instead.", ReplaceWith("IfNoneMatch"))
    @get:JvmName("getIfNoneMatch")
    public val _IfNoneMatch: String = IfNoneMatch

    @Deprecated("Use IfRange constant instead.", ReplaceWith("IfRange"))
    @get:JvmName("getIfRange")
    public val _IfRange: String = IfRange

    @Deprecated("Use IfScheduleTagMatch constant instead.", ReplaceWith("IfScheduleTagMatch"))
    @get:JvmName("getIfScheduleTagMatch")
    public val _IfScheduleTagMatch: String = IfScheduleTagMatch

    @Deprecated("Use IfUnmodifiedSince constant instead.", ReplaceWith("IfUnmodifiedSince"))
    @get:JvmName("getIfUnmodifiedSince")
    public val _IfUnmodifiedSince: String = IfUnmodifiedSince

    @Deprecated("Use LastModified constant instead.", ReplaceWith("LastModified"))
    @get:JvmName("getLastModified")
    public val _LastModified: String = LastModified

    @Deprecated("Use Location constant instead.", ReplaceWith("Location"))
    @get:JvmName("getLocation")
    public val _Location: String = Location

    @Deprecated("Use LockToken constant instead.", ReplaceWith("LockToken"))
    @get:JvmName("getLockToken")
    public val _LockToken: String = LockToken

    @Deprecated("Use Link constant instead.", ReplaceWith("Link"))
    @get:JvmName("getLink")
    public val _Link: String = Link

    @Deprecated("Use MaxForwards constant instead.", ReplaceWith("MaxForwards"))
    @get:JvmName("getMaxForwards")
    public val _MaxForwards: String = MaxForwards

    @Deprecated("Use MIMEVersion constant instead.", ReplaceWith("MIMEVersion"))
    @get:JvmName("getMIMEVersion")
    public val _MIMEVersion: String = MIMEVersion

    @Deprecated("Use OrderingType constant instead.", ReplaceWith("OrderingType"))
    @get:JvmName("getOrderingType")
    public val _OrderingType: String = OrderingType

    @Deprecated("Use Origin constant instead.", ReplaceWith("Origin"))
    @get:JvmName("getOrigin")
    public val _Origin: String = Origin

    @Deprecated("Use Overwrite constant instead.", ReplaceWith("Overwrite"))
    @get:JvmName("getOverwrite")
    public val _Overwrite: String = Overwrite

    @Deprecated("Use Position constant instead.", ReplaceWith("Position"))
    @get:JvmName("getPosition")
    public val _Position: String = Position

    @Deprecated("Use Pragma constant instead.", ReplaceWith("Pragma"))
    @get:JvmName("getPragma")
    public val _Pragma: String = Pragma

    @Deprecated("Use Prefer constant instead.", ReplaceWith("Prefer"))
    @get:JvmName("getPrefer")
    public val _Prefer: String = Prefer

    @Deprecated("Use PreferenceApplied constant instead.", ReplaceWith("PreferenceApplied"))
    @get:JvmName("getPreferenceApplied")
    public val _PreferenceApplied: String = PreferenceApplied

    @Deprecated("Use ProxyAuthenticate constant instead.", ReplaceWith("ProxyAuthenticate"))
    @get:JvmName("getProxyAuthenticate")
    public val _ProxyAuthenticate: String = ProxyAuthenticate

    @Deprecated("Use ProxyAuthenticationInfo constant instead.", ReplaceWith("ProxyAuthenticationInfo"))
    @get:JvmName("getProxyAuthenticationInfo")
    public val _ProxyAuthenticationInfo: String = ProxyAuthenticationInfo

    @Deprecated("Use ProxyAuthorization constant instead.", ReplaceWith("ProxyAuthorization"))
    @get:JvmName("getProxyAuthorization")
    public val _ProxyAuthorization: String = ProxyAuthorization

    @Deprecated("Use PublicKeyPins constant instead.", ReplaceWith("PublicKeyPins"))
    @get:JvmName("getPublicKeyPins")
    public val _PublicKeyPins: String = PublicKeyPins

    @Deprecated("Use PublicKeyPinsReportOnly constant instead.", ReplaceWith("PublicKeyPinsReportOnly"))
    @get:JvmName("getPublicKeyPinsReportOnly")
    public val _PublicKeyPinsReportOnly: String = PublicKeyPinsReportOnly

    @Deprecated("Use Range constant instead.", ReplaceWith("Range"))
    @get:JvmName("getRange")
    public val _Range: String = Range

    @Deprecated("Use Referrer constant instead.", ReplaceWith("Referrer"))
    @get:JvmName("getReferrer")
    public val _Referrer: String = Referrer

    @Deprecated("Use RetryAfter constant instead.", ReplaceWith("RetryAfter"))
    @get:JvmName("getRetryAfter")
    public val _RetryAfter: String = RetryAfter

    @Deprecated("Use ScheduleReply constant instead.", ReplaceWith("ScheduleReply"))
    @get:JvmName("getScheduleReply")
    public val _ScheduleReply: String = ScheduleReply

    @Deprecated("Use ScheduleTag constant instead.", ReplaceWith("ScheduleTag"))
    @get:JvmName("getScheduleTag")
    public val _ScheduleTag: String = ScheduleTag

    @Deprecated("Use SecWebSocketAccept constant instead.", ReplaceWith("SecWebSocketAccept"))
    @get:JvmName("getSecWebSocketAccept")
    public val _SecWebSocketAccept: String = SecWebSocketAccept

    @Deprecated("Use SecWebSocketExtensions constant instead.", ReplaceWith("SecWebSocketExtensions"))
    @get:JvmName("getSecWebSocketExtensions")
    public val _SecWebSocketExtensions: String = SecWebSocketExtensions

    @Deprecated("Use SecWebSocketKey constant instead.", ReplaceWith("SecWebSocketKey"))
    @get:JvmName("getSecWebSocketKey")
    public val _SecWebSocketKey: String = SecWebSocketKey

    @Deprecated("Use SecWebSocketProtocol constant instead.", ReplaceWith("SecWebSocketProtocol"))
    @get:JvmName("getSecWebSocketProtocol")
    public val _SecWebSocketProtocol: String = SecWebSocketProtocol

    @Deprecated("Use SecWebSocketVersion constant instead.", ReplaceWith("SecWebSocketVersion"))
    @get:JvmName("getSecWebSocketVersion")
    public val _SecWebSocketVersion: String = SecWebSocketVersion

    @Deprecated("Use Server constant instead.", ReplaceWith("Server"))
    @get:JvmName("getServer")
    public val _Server: String = Server

    @Deprecated("Use SetCookie constant instead.", ReplaceWith("SetCookie"))
    @get:JvmName("getSetCookie")
    public val _SetCookie: String = SetCookie

    @Deprecated("Use SLUG constant instead.", ReplaceWith("SLUG"))
    @get:JvmName("getSLUG")
    public val _SLUG: String = SLUG

    @Deprecated("Use StrictTransportSecurity constant instead.", ReplaceWith("StrictTransportSecurity"))
    @get:JvmName("getStrictTransportSecurity")
    public val _StrictTransportSecurity: String = StrictTransportSecurity

    @Deprecated("Use TE constant instead.", ReplaceWith("TE"))
    @get:JvmName("getTE")
    public val _TE: String = TE

    @Deprecated("Use Timeout constant instead.", ReplaceWith("Timeout"))
    @get:JvmName("getTimeout")
    public val _Timeout: String = Timeout

    @Deprecated("Use Trailer constant instead.", ReplaceWith("Trailer"))
    @get:JvmName("getTrailer")
    public val _Trailer: String = Trailer

    @Deprecated("Use TransferEncoding constant instead.", ReplaceWith("TransferEncoding"))
    @get:JvmName("getTransferEncoding")
    public val _TransferEncoding: String = TransferEncoding

    @Deprecated("Use Upgrade constant instead.", ReplaceWith("Upgrade"))
    @get:JvmName("getUpgrade")
    public val _Upgrade: String = Upgrade

    @Deprecated("Use UserAgent constant instead.", ReplaceWith("UserAgent"))
    @get:JvmName("getUserAgent")
    public val _UserAgent: String = UserAgent

    @Deprecated("Use Vary constant instead.", ReplaceWith("Vary"))
    @get:JvmName("getVary")
    public val _Vary: String = Vary

    @Deprecated("Use Via constant instead.", ReplaceWith("Via"))
    @get:JvmName("getVia")
    public val _Via: String = Via

    @Deprecated("Use Warning constant instead.", ReplaceWith("Warning"))
    @get:JvmName("getWarning")
    public val _Warning: String = Warning

    @Deprecated("Use WWWAuthenticate constant instead.", ReplaceWith("WWWAuthenticate"))
    @get:JvmName("getWWWAuthenticate")
    public val _WWWAuthenticate: String = WWWAuthenticate

    @Deprecated("Use AccessControlAllowOrigin constant instead.", ReplaceWith("AccessControlAllowOrigin"))
    @get:JvmName("getAccessControlAllowOrigin")
    public val _AccessControlAllowOrigin: String = AccessControlAllowOrigin

    @Deprecated("Use AccessControlAllowMethods constant instead.", ReplaceWith("AccessControlAllowMethods"))
    @get:JvmName("getAccessControlAllowMethods")
    public val _AccessControlAllowMethods: String = AccessControlAllowMethods

    @Deprecated("Use AccessControlAllowCredentials constant instead.", ReplaceWith("AccessControlAllowCredentials"))
    @get:JvmName("getAccessControlAllowCredentials")
    public val _AccessControlAllowCredentials: String = AccessControlAllowCredentials

    @Deprecated("Use AccessControlAllowHeaders constant instead.", ReplaceWith("AccessControlAllowHeaders"))
    @get:JvmName("getAccessControlAllowHeaders")
    public val _AccessControlAllowHeaders: String = AccessControlAllowHeaders

    @Deprecated("Use AccessControlRequestMethod constant instead.", ReplaceWith("AccessControlRequestMethod"))
    @get:JvmName("getAccessControlRequestMethod")
    public val _AccessControlRequestMethod: String = AccessControlRequestMethod

    @Deprecated("Use AccessControlRequestHeaders constant instead.", ReplaceWith("AccessControlRequestHeaders"))
    @get:JvmName("getAccessControlRequestHeaders")
    public val _AccessControlRequestHeaders: String = AccessControlRequestHeaders

    @Deprecated("Use AccessControlExposeHeaders constant instead.", ReplaceWith("AccessControlExposeHeaders"))
    @get:JvmName("getAccessControlExposeHeaders")
    public val _AccessControlExposeHeaders: String = AccessControlExposeHeaders

    @Deprecated("Use AccessControlMaxAge constant instead.", ReplaceWith("AccessControlMaxAge"))
    @get:JvmName("getAccessControlMaxAge")
    public val _AccessControlMaxAge: String = AccessControlMaxAge

    @Deprecated("Use XHttpMethodOverride constant instead.", ReplaceWith("XHttpMethodOverride"))
    @get:JvmName("getXHttpMethodOverride")
    public val _XHttpMethodOverride: String = XHttpMethodOverride

    @Deprecated("Use XForwardedHost constant instead.", ReplaceWith("XForwardedHost"))
    @get:JvmName("getXForwardedHost")
    public val _XForwardedHost: String = XForwardedHost

    @Deprecated("Use XForwardedServer constant instead.", ReplaceWith("XForwardedServer"))
    @get:JvmName("getXForwardedServer")
    public val _XForwardedServer: String = XForwardedServer

    @Deprecated("Use XForwardedProto constant instead.", ReplaceWith("XForwardedProto"))
    @get:JvmName("getXForwardedProto")
    public val _XForwardedProto: String = XForwardedProto

    @Deprecated("Use XForwardedFor constant instead.", ReplaceWith("XForwardedFor"))
    @get:JvmName("getXForwardedFor")
    public val _XForwardedFor: String = XForwardedFor

    @Deprecated("Use XForwardedPort constant instead.", ReplaceWith("XForwardedPort"))
    @get:JvmName("getXForwardedPort")
    public val _XForwardedPort: String = XForwardedPort

    @Deprecated("Use XRequestId constant instead.", ReplaceWith("XRequestId"))
    @get:JvmName("getXRequestId")
    public val _XRequestId: String = XRequestId

    @Deprecated("Use XCorrelationId constant instead.", ReplaceWith("XCorrelationId"))
    @get:JvmName("getXCorrelationId")
    public val _XCorrelationId: String = XCorrelationId

    @Deprecated("Use XTotalCount constant instead.", ReplaceWith("XTotalCount"))
    @get:JvmName("getXTotalCount")
    public val _XTotalCount: String = XTotalCount

    @Deprecated("Use LastEventID constant instead.", ReplaceWith("LastEventID"))
    @get:JvmName("getLastEventID")
    public val _LastEventID: String = LastEventID
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
