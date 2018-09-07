package io.ktor.http

/**
 * Represents request address information is used to make a call.
 * There are at least two possible instances: "local" is how we see request at the server application and
 * "actual" is what we can recover from proxy provided headers.
 */
interface RequestConnectionPoint {
    /**
     * Request scheme, for example "http" or "https"
     */
    val scheme: String

    /**
     * Protocol version string
     */
    val version: String

    /**
     * Request port, for example 80 or 443
     */
    val port: Int

    /**
     * Request host, useful for virtual hosts routing
     */
    val host: String

    /**
     * URI path with no host, port and no schema specification, but possibly with query
     */
    val uri: String

    /**
     * Request HTTP method
     */
    val method: HttpMethod

    /**
     * Client address or host name (generally not resolved to name for performance reasons).
     * For [io.ktor.application.ApplicationRequest.local] instance could point to
     * a proxy our application running behind.
     * NEVER use it for user authentication as it can be easily falsified (user can simply set some HTTP headers
     * such as X-Forwarded-Host so you should NEVER rely on it in any security checks.
     * If you are going to use it to create a back-connection please do it with care as an offender can easily
     * use it to force you to connect to some host that is not intended to be connected to so that may cause
     * serious consequences.
     */
    val remoteHost: String
}