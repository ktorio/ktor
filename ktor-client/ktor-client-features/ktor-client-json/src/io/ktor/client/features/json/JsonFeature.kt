package io.ktor.client.features.json

import io.ktor.client.*
import io.ktor.client.features.*
import io.ktor.client.request.*
import io.ktor.client.response.*
import io.ktor.content.*
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
            val textFeature = scope.feature(HttpPlainText) ?: error("HttpPlainText feature should be installed to read payload")

            scope.requestPipeline.intercept(HttpRequestPipeline.Transform) { request: HttpRequestBuilder ->
                request.header(HttpHeaders.Accept, ContentType.Application.Json.toString())

                if (request.contentType()?.match(ContentType.Application.Json) != true) return@intercept

                val body = request.body
                request.body = feature.serializer.write(body)
            }

            scope.responsePipeline.intercept(HttpResponsePipeline.Transform) { (expectedType, response) ->
                if (response !is IncomingContent || context.response.contentType()?.match(ContentType.Application.Json) != true) return@intercept

                val content = textFeature.read(response)
                proceedWith(HttpResponseContainer(expectedType, feature.serializer.read(expectedType, content)))
            }
        }
    }
}