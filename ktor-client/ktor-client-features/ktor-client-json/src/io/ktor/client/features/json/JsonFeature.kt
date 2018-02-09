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

        override fun prepare(block: Config.() -> Unit): JsonFeature =
                Config().apply(block).let { JsonFeature(it.serializer) }

        override fun install(feature: JsonFeature, scope: HttpClient) {
            scope.requestPipeline.intercept(HttpRequestPipeline.Transform) { payload ->

                context.accept(ContentType.Application.Json)
                if (context.contentType()?.match(ContentType.Application.Json) != true) {
                    return@intercept
                }

                context.headers.remove(HttpHeaders.ContentType)
                proceedWith(feature.serializer.write(payload))
            }

            scope.responsePipeline.intercept(HttpResponsePipeline.Transform) { (expectedType, response) ->
                if (response !is HttpResponse || context.response.contentType()?.match(ContentType.Application.Json) != true) return@intercept

                proceedWith(HttpResponseContainer(
                        expectedType,
                        feature.serializer.read(expectedType, response)
                ))
            }
        }
    }
}