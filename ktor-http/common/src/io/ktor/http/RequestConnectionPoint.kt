/*
* Copyright 2014-2021 JetBrains s.r.o and contributors. Use of this source code is governed by the Apache 2.0 license.
*/

package io.ktor.http

/**
 * Represents request address information is used to make a call.
 * There are at least two possible instances: "local" is how we see request at the server application and
 * "actual" is what we can recover from proxy provided headers.
 *
 * [Report a problem](https://ktor.io/feedback/?fqname=io.ktor.http.RequestConnectionPoint)
 */
public interface RequestConnectionPoint {
    /**
     * Scheme, for example "http" or "https"
     *
     * [Report a problem](https://ktor.io/feedback/?fqname=io.ktor.http.RequestConnectionPoint.scheme)
     */
    public val scheme: String

    /**
     * Protocol version string
     *
     * [Report a problem](https://ktor.io/feedback/?fqname=io.ktor.http.RequestConnectionPoint.version)
     */
    public val version: String

    /**
     * Port, for example 80 or 443
     *
     * [Report a problem](https://ktor.io/feedback/?fqname=io.ktor.http.RequestConnectionPoint.port)
     */
    @Deprecated(
        "Use localPort or serverPort instead",
        level = DeprecationLevel.ERROR
    )
    public val port: Int

    /**
     * Port on which the request was received, for example 80 or 443
     *
     * [Report a problem](https://ktor.io/feedback/?fqname=io.ktor.http.RequestConnectionPoint.localPort)
     */
    public val localPort: Int

    /**
     * Port to which the request was sent, for example, 80 or 443
     *
     * [Report a problem](https://ktor.io/feedback/?fqname=io.ktor.http.RequestConnectionPoint.serverPort)
     */
    public val serverPort: Int

    /**
     * Request host, useful for virtual hosts routing
     *
     * [Report a problem](https://ktor.io/feedback/?fqname=io.ktor.http.RequestConnectionPoint.host)
     */
    @Deprecated(
        "Use localHost or serverHost instead",
        level = DeprecationLevel.ERROR
    )
    public val host: String

    /**
     * Host on which the request was received, is useful for virtual hosts routing
     *
     * [Report a problem](https://ktor.io/feedback/?fqname=io.ktor.http.RequestConnectionPoint.localHost)
     */
    public val localHost: String

    /**
     * Host to which the request was sent, is useful for virtual hosts routing
     *
     * [Report a problem](https://ktor.io/feedback/?fqname=io.ktor.http.RequestConnectionPoint.serverHost)
     */
    public val serverHost: String

    /**
     * IP address on which the request was received.
     *
     * [Report a problem](https://ktor.io/feedback/?fqname=io.ktor.http.RequestConnectionPoint.localAddress)
     */
    public val localAddress: String

    /**
     * URI path with no host, port and no schema specification, but possibly with query
     *
     * [Report a problem](https://ktor.io/feedback/?fqname=io.ktor.http.RequestConnectionPoint.uri)
     */
    public val uri: String

    /**
     * Request HTTP method
     *
     * [Report a problem](https://ktor.io/feedback/?fqname=io.ktor.http.RequestConnectionPoint.method)
     */
    public val method: HttpMethod

    /**
     * Client address or host name if it can be resolved.
     * For [io.ktor.server.request.ApplicationRequest.local] instance could point to
     * a proxy our application running behind.
     * NEVER use it for user authentication as it can be easily falsified (user can simply set some HTTP headers
     * such as X-Forwarded-Host so you should NEVER rely on it in any security checks).
     * If you are going to use it to create a back-connection, please do it with care as an offender can easily
     * use it to force you to connect to some host that is not intended to be connected to so that may cause
     * serious consequences.
     *
     * [Report a problem](https://ktor.io/feedback/?fqname=io.ktor.http.RequestConnectionPoint.remoteHost)
     */
    public val remoteHost: String

    /**
     * Client port.
     * For [io.ktor.server.request.ApplicationRequest.local.local] instance could point to
     * a proxy our application running behind.
     * NEVER use it for user authentication as it can be easily falsified (user can simply set some HTTP headers
     * such as X-Forwarded-Host so you should NEVER rely on it in any security checks).
     * If you are going to use it to create a back-connection, please do it with care as an offender can easily
     * use it to force you to connect to some host that is not intended to be connected to so that may cause
     * serious consequences.
     *
     * [Report a problem](https://ktor.io/feedback/?fqname=io.ktor.http.RequestConnectionPoint.remotePort)
     */
    public val remotePort: Int

    /**
     * Client address.
     * For [io.ktor.server.request.ApplicationRequest.local] instance could point to
     * a proxy our application running behind.
     * NEVER use it for user authentication as it can be easily falsified (user can simply set some HTTP headers
     * such as X-Forwarded-Host so you should NEVER rely on it in any security checks).
     * If you are going to use it to create a back-connection, please do it with care as an offender can easily
     * use it to force you to connect to some host that is not intended to be connected to so that may cause
     * serious consequences.
     *
     * [Report a problem](https://ktor.io/feedback/?fqname=io.ktor.http.RequestConnectionPoint.remoteAddress)
     */
    public val remoteAddress: String
}
