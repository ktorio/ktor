package io.ktor.http

class URLBuilder(
        var protocol: URLProtocol = URLProtocol.HTTP,
        var host: String = "localhost",
        var port: Int = protocol.defaultPort,
        var user: String? = null,
        var password: String? = null,
        var encodedPath: String = "/",
        val parameters: ParametersBuilder = ParametersBuilder(),
        var fragment: String = "",
        var trailingQuery: Boolean = false
) {
    fun path(vararg components: String) {
        path(components.asList())
    }

    fun path(components: List<String>) {
        encodedPath = components.joinToString("/", prefix = "/") { encodeURLPart(it) }
    }

    private fun <A : Appendable> appendTo(out: A): A {
        out.append(protocol.name)
        out.append("://")
        user?.let { usr ->
            out.append(encodeURLPart(usr))
            password?.let { pwd ->
                out.append(":")
                out.append(encodeURLPart(pwd))
            }
            out.append("@")
        }
        out.append(host)

        if (port != protocol.defaultPort) {
            out.append(":")
            out.append(port.toString())
        }

        if (!encodedPath.startsWith("/")) {
            out.append('/')
        }

        out.append(encodedPath)

        val queryParameters = parameters.build()
        if (!queryParameters.isEmpty() || trailingQuery) {
            out.append("?")
        }

        queryParameters.formUrlEncodeTo(out)

        if (fragment.isNotEmpty()) {
            out.append('#')
            out.append(encodeURLPart(fragment))
        }

        return out
    }

    // note: 256 should fit 99.5% of all urls according to http://www.supermind.org/blog/740/average-length-of-a-url-part-2
    fun buildString(): String = appendTo(StringBuilder(256)).toString()

    fun build(): Url = Url(
            protocol, host, port, encodedPath, parameters.build(), fragment, user, password, trailingQuery
    )

    // Required to write external extension function
    companion object
}

data class Url(
        val protocol: URLProtocol,
        val host: String,
        val port: Int,
        val encodedPath: String,
        val parameters: Parameters,
        val fragment: String,
        val user: String?,
        val password: String?,
        val trailingQuery: Boolean
)

