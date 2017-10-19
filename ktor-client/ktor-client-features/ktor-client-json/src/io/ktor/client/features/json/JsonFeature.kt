package io.ktor.client.features.json

import io.ktor.client.*
import io.ktor.client.features.*
import io.ktor.client.request.*
import io.ktor.client.response.*
import io.ktor.http.*
import io.ktor.pipeline.*
import io.ktor.util.*


class JsonFeature(val serializer: JsonSerializer) {
    class Config {
        var serializer: JsonSerializer = GsonSerializer()
    }

    companion object Feature : HttpClientFeature<Config, JsonFeature> {
        override val key: AttributeKey<JsonFeature> = AttributeKey("Json")

        override fun prepare(block: Config.() -> Unit): JsonFeature = Config().apply(block).let { JsonFeature(it.serializer) }

        override fun install(feature: JsonFeature, scope: HttpClient) {
            scope.requestPipeline.intercept(HttpRequestPipeline.Transform) { builder: HttpRequestBuilder ->
                builder.header(HttpHeaders.Accept, ContentType.Application.Json.toString())
            }

            scope.responsePipeline.intercept(HttpResponsePipeline.Transform) { (expectedType, _, response) ->
                if (response.contentType() != ContentType.Application.Json) return@intercept

                val reader = scope.feature(HttpPlainText)
                        ?: error("HttpPlainText feature should be installed to read payload")

                val content = reader.read(response)
                        ?: error("Failed to read json text")

                response.payload = feature.serializer.read(expectedType, content)
            }
        }
    }
}