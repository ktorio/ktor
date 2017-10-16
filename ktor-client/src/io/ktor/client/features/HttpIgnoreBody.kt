package io.ktor.client.features

import io.ktor.client.HttpClient
import io.ktor.client.response.HttpResponsePipeline
import io.ktor.util.AttributeKey


class HttpIgnoreBody {
    companion object Feature : HttpClientFeature<Unit, HttpIgnoreBody> {

        override fun prepare(block: Unit.() -> Unit): HttpIgnoreBody = HttpIgnoreBody()

        override val key: AttributeKey<HttpIgnoreBody> = AttributeKey("HttpIgnoreBody")

        override fun install(feature: HttpIgnoreBody, scope: HttpClient) {
            scope.responsePipeline.intercept(HttpResponsePipeline.Transform) { data ->
                if (data.expectedType != Unit::class) {
                    return@intercept
                }

                data.response.payload = Unit
            }
        }

    }
}