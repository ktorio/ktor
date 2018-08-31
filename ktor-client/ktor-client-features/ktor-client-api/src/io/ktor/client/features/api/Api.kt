package io.ktor.client.features.api

import io.ktor.client.HttpClient
import io.ktor.client.HttpClientConfig
import io.ktor.client.features.HttpClientFeature
import io.ktor.client.request.HttpRequestPipeline
import io.ktor.http.URLProtocol
import io.ktor.util.AttributeKey

class Api(val config: Configuration) {

    class Configuration {
        var scheme: String = "http"
        var host: String = "localhost"
        var port: Int = 80
        var basePath: String = "/"

    }

    companion object Feature : HttpClientFeature<Configuration, Api> {

        override val key: AttributeKey<Api> = AttributeKey("Api")

        override fun prepare(block: Configuration.() -> Unit): Api {
            val config = Configuration()
            config.block()
            return Api(config)
        }

        override fun install(feature: Api, scope: HttpClient) {
            scope.requestPipeline.intercept(HttpRequestPipeline.Before) { _ ->
                val path = context.url.encodedPath
                context.url {
                    protocol = URLProtocol.createOrDefault(feature.config.scheme)
                    host = feature.config.host
                    port = feature.config.port
                    encodedPath = feature.config.basePath + path
                }
            }
        }
    }
}

fun HttpClientConfig<*>.api(config: Api.Configuration.() -> Unit) {
    install(Api, config)
}