/*
 * Copyright 2014-2019 JetBrains s.r.o and contributors. Use of this source code is governed by the Apache 2.0 license.
 */

package io.ktor.http

@Suppress("unused", "KDocMissingDocumentation", "PublicApiImplicitType", "MayBeConstant")
object HttpHeaders {
    // Permanently registered standard HTTP headers
    // The list is taken from http://www.iana.org/assignments/message-headers/message-headers.xml#perm-headers
    // As of HTTP/2, header names must be lowercase (https://tools.ietf.org/html/rfc7540#section-8.1.2).
    // Since HTTP/1 headers are case insensitive, this is backwards compatible.

    val Accept = "accept"
    val AcceptCharset = "accept-charset"
    val AcceptEncoding = "accept-encoding"
    val AcceptLanguage = "accept-language"
    val AcceptRanges = "accept-ranges"
    val Age = "age"
    val Allow = "allow"
    val ALPN = "alpn" // Application-Layer Protocol Negotiation, HTTP/2
    val AuthenticationInfo = "authentication-info"
    val Authorization = "authorization"
    val CacheControl = "cache-control"
    val Connection = "connection"
    val ContentDisposition = "content-disposition"
    val ContentEncoding = "content-encoding"
    val ContentLanguage = "content-language"
    val ContentLength = "content-length"
    val ContentLocation = "content-location"
    val ContentRange = "content-range"
    val ContentType = "content-type"
    val Cookie = "cookie"
    val DASL = "dasl" // WebDAV Search
    val Date = "date"
    val DAV = "dav" // WebDAV
    val Depth = "depth" // WebDAV
    val Destination = "destination"
    val ETag = "etag"
    val Expect = "expect"
    val Expires = "expires"
    val From = "from"
    val Forwarded = "forwarded"
    val Host = "host"
    val HTTP2Settings = "http2-settings"
    val If = "if"
    val IfMatch = "if-match"
    val IfModifiedSince = "if-modified-since"
    val IfNoneMatch = "if-none-match"
    val IfRange = "if-range"
    val IfScheduleTagMatch = "if-schedule-tag-match"
    val IfUnmodifiedSince = "if-unmodified-since"
    val LastModified = "last-modified"
    val Location = "location"
    val LockToken = "lock-token"
    val Link = "link"
    val MaxForwards = "max-forwards"
    val MIMEVersion = "mime-version"
    val OrderingType = "ordering-type"
    val Origin = "origin"
    val Overwrite = "overwrite"
    val Position = "position"
    val Pragma = "pragma"
    val Prefer = "prefer"
    val PreferenceApplied = "preference-applied"
    val ProxyAuthenticate = "proxy-authenticate"
    val ProxyAuthenticationInfo = "proxy-authentication-info"
    val ProxyAuthorization = "proxy-authorization"
    val PublicKeyPins = "public-key-pins"
    val PublicKeyPinsReportOnly = "public-key-pins-report-only"
    val Range = "range"
    val Referrer = "referer"
    val RetryAfter = "retry-after"
    val ScheduleReply = "schedule-reply"
    val ScheduleTag = "schedule-tag"
    val SecWebSocketAccept = "sec-websocket-accept"
    val SecWebSocketExtensions = "sec-websocket-extensions"
    val SecWebSocketKey = "sec-websocket-key"
    val SecWebSocketProtocol = "sec-websocket-protocol"
    val SecWebSocketVersion = "sec-websocket-version"
    val Server = "server"
    val SetCookie = "set-cookie"
    val SLUG = "slug" // Atom Publishing
    val StrictTransportSecurity = "strict-transport-security"
    val TE = "te"
    val Timeout = "timeout"
    val Trailer = "trailer"
    val TransferEncoding = "transfer-encoding"
    val Upgrade = "upgrade"
    val UserAgent = "user-agent"
    val Vary = "vary"
    val Via = "via"
    val Warning = "warning"
    val WWWAuthenticate = "www-authenticate"

    // CORS
    val AccessControlAllowOrigin = "access-control-allow-origin"
    val AccessControlAllowMethods = "access-control-allow-methods"
    val AccessControlAllowCredentials = "access-control-allow-credentials"
    val AccessControlAllowHeaders = "access-control-allow-headers"

    val AccessControlRequestMethod = "access-control-request-method"
    val AccessControlRequestHeaders = "access-control-request-headers"
    val AccessControlExposeHeaders = "access-control-expose-headers"
    val AccessControlMaxAge = "access-control-max-age"

    // Unofficial de-facto headers
    val XHttpMethodOverride = "x-http-method-override"
    val XForwardedHost = "x-forwarded-host"
    val XForwardedServer = "x-forwarded-server"
    val XForwardedProto = "x-forwarded-proto"
    val XForwardedFor = "x-forwarded-for"

    val XRequestId = "x-request-id"
    val XCorrelationId = "x-correlation-id"

    /**
     * Check if [header] is unsafe. Header is unsafe if listed in [UnsafeHeaders]
     */
    fun isUnsafe(header: String): Boolean = UnsafeHeaders.any { it.equals(header, ignoreCase = true) }

    val UnsafeHeaders: Array<String> = arrayOf(ContentLength, ContentType, TransferEncoding, Upgrade)
}

/**
 * Thrown when an attempt to set unsafe header detected. A header is unsafe if listed in [HttpHeaders.UnsafeHeaders].
 */
class UnsafeHeaderException(header: String) : IllegalArgumentException(
    "Header $header is controlled by the engine and " +
        "cannot be set explicitly"
)
