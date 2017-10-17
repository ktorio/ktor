package io.ktor.client.utils

import io.ktor.http.formUrlEncode
import io.ktor.util.ValuesMap
import io.ktor.util.ValuesMapBuilder
import java.net.URI


typealias Parameters = ValuesMap

typealias ParametersBuilder = ValuesMapBuilder

data class Url(
        val scheme: String,
        val host: String,
        val port: Int,
        val path: String,
        val queryParameters: Parameters,
        val username: String?,
        val password: String?
) {
    override fun toString(): String = URI(
            scheme, username, host, port, path,
            if (queryParameters.isEmpty()) null else queryParameters.formUrlEncode(),
            null
    ).toString()
}

class UrlBuilder {
    var scheme: String = "http"
        set(value) {
            if (scheme == "file") error("URL scheme file is currently not supported")
            field = value
        }

    var host: String = "localhost"

    var port: Int = 80

    var path: String = ""

    var username: String? = null

    var password: String? = null

    var queryParameters = ParametersBuilder()

    fun build(): Url = Url(scheme, host, port, path, queryParameters.build(), username, password)
}
