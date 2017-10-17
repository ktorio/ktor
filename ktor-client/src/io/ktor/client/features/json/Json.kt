package io.ktor.client.features.json

import io.ktor.client.*
import io.ktor.client.features.*
import io.ktor.client.request.*
import io.ktor.client.response.*
import io.ktor.http.*
import io.ktor.util.*


class Json(val serializer: JsonSerializer) {

    class Config {
        var serializer: JsonSerializer = GsonSerializer()
    }

    companion object Feature : HttpClientFeature<Config, Json> {
        override val key: AttributeKey<Json> = AttributeKey("json")

        override fun prepare(block: Config.() -> Unit): Json = Config().apply(block).let { Json(it.serializer) }

        override fun install(feature: Json, scope: HttpClient) {
            scope.requestPipeline.intercept(HttpRequestPipeline.Transform) { request ->
                val requestBuilder = request.safeAs<HttpRequestBuilder>() ?: return@intercept
                requestBuilder.accept(ContentType.Application.Json)
            }

            scope.responsePipeline.intercept(HttpResponsePipeline.Transform) { (expectedType, _, response) ->
                if (response.contentType()?.match(ContentType.Application.Json) != true) return@intercept
                val reader = scope.feature(HttpPlainText) ?: return@intercept
                val content = reader.read(response) ?: return@intercept

                response.payload = feature.serializer.read(expectedType, content)
            }
        }
    }
}
