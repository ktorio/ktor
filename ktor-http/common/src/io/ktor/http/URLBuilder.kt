/*
* Copyright 2014-2021 JetBrains s.r.o and contributors. Use of this source code is governed by the Apache 2.0 license.
*/

package io.ktor.http

/**
 * Select default port value from protocol.
 */
public const val DEFAULT_PORT: Int = 0

/**
 * A URL builder with all mutable components
 *
 * @property protocol URL protocol (scheme)
 * @property host name without port (domain)
 * @property port port number
 * @property user username part (optional)
 * @property password password part (optional)
 * @property encodedPath encoded URL path without query
 * @property parameters URL query parameters
 * @property fragment URL fragment (anchor name)
 * @property trailingQuery keep a trailing question character even if there are no query parameters
 */
public class URLBuilder(
    public var protocol: URLProtocol = URLProtocol.HTTP,
    public var host: String = "localhost",
    public var port: Int = DEFAULT_PORT,
    public var user: String? = null,
    public var password: String? = null,
    public var encodedPath: String = "/",
    public val parameters: ParametersBuilder = ParametersBuilder(),
    public var fragment: String = "",
    public var trailingQuery: Boolean = false
) {
    init {
        originHost?.let { takeFrom(it) }

        if (encodedPath.isEmpty()) {
            encodedPath = "/"
        }
    }

    /**
     * Encode [components] to [encodedPath]
     */
    public fun path(vararg components: String): URLBuilder {
        path(components.asList())

        return this
    }

    /**
     * Encode [components] to [encodedPath]
     */
    public fun path(components: List<String>): URLBuilder {
        encodedPath = components.joinToString("/", prefix = "/") { it.encodeURLPath() }

        return this
    }

    private fun <A : Appendable> appendTo(out: A): A {
        out.append(protocol.name)

        when (protocol.name) {
            "file" -> {
                out.appendFile(host, encodedPath)
                return out
            }
            "mailto" -> {
                out.appendMailto(userAndPassword, encodedPath)
                return out
            }
        }

        out.append("://")
        out.append(authority)

        out.appendUrlFullPath(encodedPath, parameters, trailingQuery)

        if (fragment.isNotEmpty()) {
            out.append('#')
            out.append(fragment.encodeURLQueryComponent())
        }

        return out
    }

    /**
     * Build a URL string
     */
    // note: 256 should fit 99.5% of all urls according to http://www.supermind.org/blog/740/average-length-of-a-url-part-2
    public fun buildString(): String = appendTo(StringBuilder(256)).toString()

    /**
     * Build a [Url] instance (everything is copied to a new instance)
     */
    public fun build(): Url = Url(
        protocol, host, port, encodedPath, parameters.build(), fragment, user, password, trailingQuery
    )

    // Required to write external extension function
    public companion object
}

/**
 * Hostname of current origin.
 *
 * It uses "localhost" for all platforms except js.
 */
internal expect val URLBuilder.Companion.originHost: String?

/**
 * Create a copy of this builder. Modifications in a copy is not reflected in the original instance and vise-versa.
 */
public fun URLBuilder.clone(): URLBuilder = URLBuilder().takeFrom(this)

/**
 * Represents an immutable URL
 *
 * @property protocol
 * @property host name without port (domain)
 * @property port the specified port or protocol default port
 * @property specifiedPort port number that was specified to override protocol's default
 * @property encodedPath encoded path without query string
 * @property parameters URL query parameters
 * @property fragment URL fragment (anchor name)
 * @property user username part of URL
 * @property password password part of URL
 * @property trailingQuery keep trailing question character even if there are no query parameters
 */
public data class Url(
    val protocol: URLProtocol,
    val host: String,
    val specifiedPort: Int,
    val encodedPath: String,
    val parameters: Parameters,
    val fragment: String,
    val user: String?,
    val password: String?,
    val trailingQuery: Boolean
) {
    init {
        require(
            specifiedPort in 1..65536 ||
                specifiedPort == DEFAULT_PORT
        ) { "port must be between 1 and 65536, or $DEFAULT_PORT if not set" }
    }

    val port: Int get() = specifiedPort.takeUnless { it == DEFAULT_PORT } ?: protocol.defaultPort

    override fun toString(): String = buildString {
        append(protocol.name)

        when (protocol.name) {
            "file" -> {
                appendFile(host, encodedPath)
                return@buildString
            }
            "mailto" -> {
                val userValue = user ?: error("User can't be empty.")
                appendMailto(userValue, host)
                return@buildString
            }
        }

        append("://")
        append(authority)
        append(fullPath)

        if (fragment.isNotEmpty()) {
            append('#')
            append(fragment)
        }
    }

    public companion object
}

private fun Appendable.appendMailto(user: String, host: String) {
    append(":")
    append(user.encodeURLParameter())
    append('@')
    append(host)
}

private fun Appendable.appendFile(host: String, encodedPath: String) {
    append("://")
    append(host)
    append(encodedPath)
}

internal val Url.userAndPassword: String
    get() = buildString {
        appendUserAndPassword(user, password)
    }

internal val URLBuilder.userAndPassword: String
    get() = buildString {
        appendUserAndPassword(user, password)
    }

private fun StringBuilder.appendUserAndPassword(user: String?, password: String?) {
    user ?: return
    append(user.encodeURLParameter())

    if (password != null) {
        append(':')
        append(password.encodeURLParameter())
    }

    append("@")
}

/**
 * [Url] authority.
 */
public val Url.authority: String
    get() = buildString {
        append(userAndPassword)

        if (specifiedPort == DEFAULT_PORT) {
            append(host)
        } else {
            append(hostWithPort)
        }
    }

/**
 * [URLBuilder] authority.
 */
public val URLBuilder.authority: String
    get() = buildString {
        append(userAndPassword)
        append(host)

        if (port != DEFAULT_PORT && port != protocol.defaultPort) {
            append(":")
            append(port.toString())
        }
    }

/**
 * Adds [components] to current [encodedPath]
 */
public fun URLBuilder.pathComponents(components: List<String>): URLBuilder {
    var paths = components
        .map { part -> part.dropWhile { it == '/' }.dropLastWhile { it == '/' }.encodeURLQueryComponent() }
        .filter { it.isNotEmpty() }
        .joinToString("/")

    // make sure that there's a slash separator at the end of current path
    if (!encodedPath.endsWith('/')) {
        paths = "/$paths"
    }
    encodedPath += paths

    return this
}

/**
 * Adds [components] to current [encodedPath]
 */
public fun URLBuilder.pathComponents(vararg components: String): URLBuilder {
    return pathComponents(components.toList())
}
