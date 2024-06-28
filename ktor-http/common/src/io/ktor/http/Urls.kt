/*
 * Copyright 2014-2024 JetBrains s.r.o and contributors. Use of this source code is governed by the Apache 2.0 license.
 */

package io.ktor.http

import io.ktor.http.Uri.*
import io.ktor.http.Url.Companion.DEFAULT_AUTHORITY
import io.ktor.http.Url.Companion.DEFAULT_PROTOCOL

/**
 * NON-universal locator. Either an unprocessed string reference, URL, or URI reference.
 */
public sealed interface Locator {
    /**
     * Converts locator into a URI reference, when assuming it forms the base of a URL.
     */
    public fun asUri(): UriReference

    /**
     * Converts locator into a relative reference, for appending to a URI reference.
     *
     * By default, this is equivalent to `asUri()`.
     */
    public fun asRelativeUri(): UriReference = asUri()
}

/**
 * Create a URL from the URI representation of this locator.
 */
public fun Locator.toUrl(): Url = asUri().toUrl()

/**
 * Either a URI or a relative reference, with all optional properties.
 *
 * See https://www.rfc-editor.org/rfc/rfc3986#section-4.1
 */
public interface UriReference: Locator {
    public companion object {
        public val EMPTY: UriReference = object : UriReference {}
    }

    /**
     * Indicates the protocol used for communicating with the URI, like HTTP, for example.
     */
    public val protocol: UrlProtocol? get() = null

    /**
     * Included when URI contains // after protocol.
     */
    public val protocolSeparator: ProtocolSeparator? get() = null

    /**
     * A combination of host, port, and user information, if present.
     */
    public val authority: Authority? get() = null

    /**
     * The host from the authority section of the URL.
     */
    public val host: String? get() = authority?.host

    /**
     * The port from the authority section of the URL.
     */
    public val port: Int? get() = authority?.port

    /**
     * The user from the authority section of the URL.
     */
    public val user: String? get() = authority?.user

    /**
     * The password from the authority section of the URL.
     */
    public val password: String? get() = authority?.password

    /**
     * The path to the resource on the given host, each segment separated with the `/` character.
     */
    public val path: Path? get() = null

    /**
     * Parameters for retrieval, appearing after the `?` character.
     */
    public val parameters: Parameters? get() = null

    /**
     * String appearing after the `#` character, usually for navigating to anchor points on a web page.
     */
    public val fragment: String? get() = null

    /**
     * Encoded host.
     */
    public val encodedHost: String? get() = host?.encodeURLPathPart()

    /**
     * Encoded user.
     */
    public val encodedUser: String? get() = user?.encodeURLPathPart()

    /**
     * Encoded password.
     */
    public val encodedPassword: String? get() = password?.encodeURLPathPart()

    /**
     * Encoded path.
     */
    public val encodedPath: String? get() = path?.toString()

    /**
     * Encoded query string.
     */
    public val encodedQuery: String? get() = parameters?.formUrlEncode()

    /**
     * Encoded fragment.
     */
    public val encodedFragment: String? get() = fragment?.encodeURLPathPart()

    /**
     * Returns this URIReference instance because there is no inherent ambiguity in this locator.
     */
    override fun asUri(): UriReference = this

    /**
     * Returns this URIReference instance because there is no inherent ambiguity in this locator.
     */
    override fun asRelativeUri(): UriReference = this
}

/**
 * Mutable form of UriReference.
 */
public interface MutableUriReference: UriReference {
    override var protocol: UrlProtocol?
    override var protocolSeparator: ProtocolSeparator?
    override var authority: Authority?
    override var host: String?
    override var port: Int?
    override var user: String?
    override var password: String?
    override var path: Path?
    override var parameters: ParametersBuilder
    override var fragment: String?
}

/**
 * Reference to a pre-processed URI, which holds onto encoded strings.
 *
 * We keep the encoded string references because not all URL-encodings are standardized, and it is important
 * that parsed URLs retain their original encoding.
 */
public interface EncodedUriReference: UriReference {
    override val authority: Authority?
        get() {
            return Authority(
                host = encodedHost?.decodeURLPart() ?: return null,
                port = port,
                userInfo = encodedUser?.let { name ->
                    UserInfo(
                        name = if (name.any { it !in USERNAME_CHARACTERS }) name else name.decodeURLPart(),
                        credential = encodedPassword?.decodeURLPart()
                    )
                },
            )
        }
    override val encodedHost: String?
    override val encodedUser: String?
    override val encodedPassword: String?
    override val encodedPath: String?
    override val encodedQuery: String?
    override val encodedFragment: String?

    override val path: Path get() = encodedPath?.let { Path.parse(it) } ?: Path.EMPTY
    override val parameters: Parameters? get() = encodedQuery?.let { parseQueryString(it, decode = true) }
    override val fragment: String? get() = encodedFragment?.decodeURLPart()
}

/**
 * Intermediate form for simplified interaction with APIs.
 *
 * This is later converted to URI using a supplied strategy later in the request processing.
 *
 * Can be any combination of segments within the URL, where defaults etc. are derived later in processing.
 *
 * When a Base URI (i.e., DefaultRequest) is supplied, this can be treated as a relative reference. If not,
 * then we can treat it like an absolute authority with an implied scheme (HTTP).
 */
public data class LocatorString(val value: String): Locator {
    /**
     * Parses into a URI reference with preference to authority when reading an ambiguous input (i.e., "localhost")
     */
    public override fun asUri(): UriReference =
        UriParser.parse(value)

    /**
     * Parses into a relative reference with preference for path when reading an ambiguous input (i.e., "index.html").
     */
    public override fun asRelativeUri(): UriReference =
        UriParser.parse(value, relative = true)
}

/**
 * Final form after parsing, used in internal processing.
 *
 * Requires strict parsing, using the expression found in https://www.rfc-editor.org/rfc/rfc3986#appendix-B.
 */
public data class Url(
    override val protocol: UrlProtocol,
    override val protocolSeparator: ProtocolSeparator?,
    override val authority: Authority,
    override val path: Path,
    override val parameters: Parameters?,
    override val fragment: String?,
    override val encodedHost: String,
    override val encodedUser: String?,
    override val encodedPassword: String?,
    override val encodedPath: String,
    override val encodedQuery: String?,
    override val encodedFragment: String?,
): UriReference {
    public companion object {
        public val DEFAULT_PROTOCOL: UrlProtocol = UrlProtocol.HTTP
        public val DEFAULT_AUTHORITY: Authority = Authority("localhost")
    }

    init {
        check(authority.host != null) { "URL must not have a null host" }
    }

    override val host: String get() = authority.host!!

    override fun toString(): String = formatToString()
}

/**
 * Represents a partial URL, where all elements are considered optional.
 */
public data class Uri(
    override val protocol: UrlProtocol?,
    override val protocolSeparator: ProtocolSeparator?,
    override val authority: Authority?,
    override val path: Path,
    override val parameters: Parameters?,
    override val fragment: String?,
): UriReference {
    override fun toString(): String = formatToString()

    /**
     * Used for locating resources when joined with URN, or can be used for referencing
     * files in the local filesystem.
     *
     * Should provide functionality for normalization (i.e., removing ".." and "." segments).
     */
    public data class Path(val segments: List<String>): UriReference {

        public companion object {
            /**
             * Empty path, with empty string value ""
             */
            public val EMPTY: Path = Path(emptyList())

            /**
             * Parses a path using '/' separator.
             */
            public fun parse(str: String): Path =
                Path(str.splitToSequence('/').map { it.decodeURLPart() }.toList())
        }

        override val path: Path get() = this

        public fun isEmpty(): Boolean = segments.isEmpty()
        public fun isRoot(): Boolean = segments.singleOrNull() == ""
        public fun isRelative(): Boolean = !isAbsolute() && !isEmpty()
        public fun isAbsolute(): Boolean = segments.firstOrNull()?.isEmpty() == true

        public operator fun plus(other: Path): Path =
            if (other.isEmpty()) {
                this
            } else {
                Path(segments.dropLast(1) + other.segments)
            }

        override fun toString(): String =
            if (isRoot()) {
                "/"
            } else {
                segments.joinToString("/") {
                    it.encodeURLPath()
                }
            }
    }

    /**
     * Generally, the host and port, but it also includes user information (normally supplied for emails).
     */
    public data class Authority(
        val userInfo: UserInfo?,
        override val host: String?,
        override val port: Int?
    ): UriReference {
        public constructor(host: String, port: Int? = null): this(null, host, port)

        override val user: String? get() = userInfo?.name
        override val password: String? get() = userInfo?.credential
        override val authority: Authority get() = this

        override fun toString(): String = buildString {
            if (userInfo != null) {
                append(userInfo, '@')
            }
            append(host ?: "")
            append(port.prefix(':'))
        }
    }

    /**
     * Section before @ in the authority.
     */
    public data class UserInfo(
        val name: String,
        val credential: String?,
    ) {
        override fun toString(): String =
            name.encodeURLParameter() + credential?.encodeURLParameter().prefix(':')
    }
}

/**
 * Explicitly convert to URL with supplied defaults.
 */
public fun UriReference.toUrl(
    defaultScheme: UrlProtocol = DEFAULT_PROTOCOL,
    defaultAuthority: Authority = DEFAULT_AUTHORITY,
): Url = Url(
    protocol ?: defaultScheme,
    protocolSeparator,
    authority + defaultAuthority,
    path ?: Path.EMPTY,
    parameters,
    fragment,
    encodedHost ?: defaultAuthority.host!!.encodeURLPathPart(),
    encodedUser,
    encodedPassword,
    encodedPath ?: "",
    encodedQuery,
    encodedFragment,
)

/**
 * When combining locator strings, we can construct URI references from their assumed roles.
 */
public operator fun Locator.plus(other: Locator): UriReference {
    return this.asUri() + other.asRelativeUri()
}

/**
 * Combining URI references merges values from the right-hand side of the operation.
 *
 * When the right-hand URI is absolute or includes an authority (host), we use its path (and query / fragment).
 * Else, we combine paths.
 *
 * TODO this behaviour is a bit weird and not universal
 */
public operator fun UriReference.plus(other: UriReference): UriReference =
    if (other.authority != null || other.path?.isAbsolute() == true) {
        Uri(
            protocol = other.protocol ?: this.protocol,
            protocolSeparator = other.protocolSeparator ?: this.protocolSeparator,
            authority = other.authority + this.authority,
            path = other.path.orEmpty(),
            parameters = if (other.parameters == null && this.parameters == null) null else other.parameters.orEmpty() + this.parameters.orEmpty(),
            fragment = other.fragment
        )
    }
    else {
        Uri(
            protocol = other.protocol ?: this.protocol,
            protocolSeparator = other.protocolSeparator ?: this.protocolSeparator,
            authority = other.authority ?: this.authority,
            path = this.path + other.path,
            parameters = if (other.parameters == null && this.parameters == null) null else other.parameters.orEmpty() + this.parameters.orEmpty(),
            fragment = other.fragment ?: this.fragment
        )
    }

public operator fun Path?.plus(other: Path?): Path = this?.let { it + other.orEmpty() } ?: other ?: Path.EMPTY
public fun Path?.orEmpty(): Path = this ?: Path.EMPTY

private operator fun Authority?.plus(other: Authority?): Authority =
    Authority(
        this?.userInfo ?: other?.userInfo,
        this?.host ?: other?.host,
        this?.port ?: other?.port,
    )

private fun Any?.prefix(ch: Char): String =
    if (this == null) "" else ch + toString()

public fun formatAuthority(
    user: String?,
    password: String?,
    host: String?,
    port: Int?
): String = buildString {
    if (user != null) {
        append(formatUserInfo(user, password), '@')
    }
    append(host ?: "")
    append(port.prefix(':'))
}

public fun formatUserInfo(name: String, password: String?): String =
    name + password?.prefix(':')

/**
 * Builds URI string from available components.
 */
// note: 256 should fit 99.5% of all urls according to http://www.supermind.org/blog/740/average-length-of-a-url-part-2
public fun UriReference.formatToString(): String = buildString(256) {
    protocol?.let { protocol ->
        append(protocol)
        append(':')
        if (protocolSeparator != null)
            append("//")
    }
    encodedHost?.let { encodedHost ->
        append(
            formatAuthority(
                encodedUser,
                encodedPassword,
                encodedHost,
                port
            )
        )
        if (path?.isRelative() == true)
            append('/')
    }
    append(path)

    encodedQuery?.let {
        append('?', it)
    }

    append(encodedFragment?.prefix('#'))
}
