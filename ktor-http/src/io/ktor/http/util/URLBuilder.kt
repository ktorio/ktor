package io.ktor.http.util

import io.ktor.http.*
import io.ktor.util.*


class URLBuilder(
        var protocol: URLProtocol = URLProtocol.HTTP,
        var host: String = "localhost",
        var port: Int = protocol.defaultPort,
        var user: String? = null,
        var password: String? = null,
        var encodedPath: String = "/",
        val parameters: ValuesMapBuilder = ValuesMapBuilder(),
        var fragment: String = ""
) {
    fun path(vararg components: String) {
        path(components.asList())
    }

    fun path(components: List<String>) {
        encodedPath = components.joinToString("/", prefix = "/") { encodeURLPart(it) }
    }

    fun <A : Appendable> appendTo(out: A): A {
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
        if (!queryParameters.isEmpty()) {
            out.append("?")
            queryParameters.formUrlEncodeTo(out)
        }

        if (fragment.isNotEmpty()) {
            out.append('#')
            out.append(encodeURLPart(fragment))
        }

        return out
    }

    // note: 256 should fit 99.5% of all urls according to http://www.supermind.org/blog/740/average-length-of-a-url-part-2
    fun build(): String = appendTo(StringBuilder(256)).toString()

    // Required to write external extension function
    companion object
}