package io.ktor.http

/**
 * Select default port value from protocol.
 */
const val DEFAULT_PORT: Int = 0

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
class URLBuilder(
    var protocol: URLProtocol = URLProtocol.HTTP,
    var host: String = "localhost",
    var port: Int = DEFAULT_PORT,
    var user: String? = null,
    var password: String? = null,
    var encodedPath: String = "/",
    val parameters: ParametersBuilder = ParametersBuilder(),
    var fragment: String = "",
    var trailingQuery: Boolean = false
) {

    /**
     * Encode [components] to [encodedPath]
     */
    fun path(vararg components: String): URLBuilder {
        path(components.asList())

        return this
    }

    /**
     * Encode [components] to [encodedPath]
     */
    fun path(components: List<String>): URLBuilder {
        encodedPath = components.joinToString("/", prefix = "/") { it.encodeURLQueryComponent() }

        return this
    }

    private fun <A : Appendable> appendTo(out: A): A {
        out.append(protocol.name)
        out.append("://")
        user?.let { user ->
            out.append(user.encodeURLParameter())
            password?.let { password ->
                out.append(":")
                out.append(password.encodeURLParameter())
            }
            out.append("@")
        }
        out.append(host)

        if (port != DEFAULT_PORT && port != protocol.defaultPort) {
            out.append(":")
            out.append(port.toString())
        }

        out.appendUrlFullPath(encodedPath, parameters.build(), trailingQuery)

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
    fun buildString(): String = appendTo(StringBuilder(256)).toString()

    /**
     * Build a [Url] instance (everything is copied to a new instance)
     */
    fun build(): Url = Url(
        protocol, host, port, encodedPath, parameters.build(), fragment, user, password, trailingQuery
    )

    // Required to write external extension function
    companion object
}

/**
 * Create a copy of this builder. Modifications in a copy is not reflected in the original instance and vise-versa.
 */
fun URLBuilder.clone(): URLBuilder = URLBuilder().takeFrom(this)

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
data class Url(
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
        require(specifiedPort in 1..65536 || specifiedPort == DEFAULT_PORT) { "port must be between 1 and 65536, or $DEFAULT_PORT if not set" }
    }

    val port: Int get() = specifiedPort.takeUnless { it == DEFAULT_PORT } ?: protocol.defaultPort

    override fun toString(): String = buildString {
        append(protocol.name)
        append("://")
        if (user != null) {
            append(user)
            if (password != null) {
                append(':')
                append(password)
            }
            append('@')
        }
        if (specifiedPort == DEFAULT_PORT) {
            append(host)
        } else {
            append(hostWithPort)
        }
        append(fullPath)
        if (fragment.isNotEmpty()) {
            append('#')
            append(fragment)
        }
    }

    companion object
}
