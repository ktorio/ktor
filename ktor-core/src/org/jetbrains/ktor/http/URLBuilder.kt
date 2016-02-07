package org.jetbrains.ktor.http

import org.jetbrains.ktor.application.*
import org.jetbrains.ktor.auth.*
import org.jetbrains.ktor.util.*

enum class URLProtocol(val defaultPort: Int) {
    HTTP(80),
    HTTPS(443);

    val nameLowerCase = this.name.toLowerCase()
}

class URLBuilder(
        var protocol: URLProtocol = URLProtocol.HTTP,
        var host: String = "localhost",
        var port: Int = protocol.defaultPort,
        var user: UserPasswordCredential? = null,
        var encodedPath: String = "/",
        val parameters: ValuesMap.Builder = ValuesMap.Builder(),
        var fragment: String = ""
) {
    fun path(vararg components: String) {
        path(components.asList())
    }

    fun path(components: List<String>) {
        encodedPath = components.joinToString("/", prefix = "/") { encodeURLPart(it) }
    }

    fun <A : Appendable> appendTo(out: A): A {
        out.append(protocol.nameLowerCase)
        out.append("://")
        user?.let {
            out.append(encodeURLPart(it.name))
            out.append(":")
            out.append(encodeURLPart(it.password))
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
}

fun url(block: URLBuilder.() -> Unit) = URLBuilder().apply(block).build()
fun ApplicationCall.url(block: URLBuilder.() -> Unit = {}): String {
    val builder = URLBuilder()
    // TODO protocol
    builder.host = request.host() ?: "localhost"
    builder.port = request.port()
    builder.encodedPath = request.path()
    builder.parameters.appendAll(request.queryParameters())

    builder.block()

    return builder.build()
}