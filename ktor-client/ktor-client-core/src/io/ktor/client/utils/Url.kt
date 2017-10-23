package io.ktor.client.utils

import io.ktor.http.*
import io.ktor.util.*
import java.net.*


typealias Parameters = ValuesMap

typealias ParametersBuilder = ValuesMapBuilder

data class Url(
        val scheme: String,
        val host: String,
        val port: Int,
        val path: String,
        val queryParameters: Parameters?,
        val fragment: String?,
        val username: String?,
        val password: String?
) {
    override fun toString(): String = URI(
            scheme, username, host, port, path,
            when (queryParameters) {
                null -> null
                else -> queryParameters.formUrlEncode()
            },
            fragment
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

    var queryParameters: ParametersBuilder? = null
        private set

    var fragment: String? = null

    var username: String? = null

    var password: String? = null

    fun addQueryParameter(name: String, value: String) {
        initQueryParameters()

        queryParameters?.append(name, value)
    }

    fun addQueryParameters(parameters: ParametersBuilder) {
        initQueryParameters()
        queryParameters?.appendAll(parameters)
    }

    fun addQueryParameters(parameters: Parameters) {
        initQueryParameters()
        queryParameters?.appendAll(parameters)
    }

    fun build(): Url = Url(
            scheme, host, port, path, queryParameters?.build(), fragment, username, password
    )

    private fun initQueryParameters() {
        if (queryParameters == null) {
            queryParameters = ParametersBuilder()
        }
    }
}
