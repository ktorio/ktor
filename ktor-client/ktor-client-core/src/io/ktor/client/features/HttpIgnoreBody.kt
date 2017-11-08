package io.ktor.client.features

import io.ktor.client.*
import io.ktor.client.response.*
import io.ktor.util.*


class HttpIgnoreBody {
    companion object Feature : HttpClientFeature<Unit, HttpIgnoreBody> {
        override val key: AttributeKey<HttpIgnoreBody> = AttributeKey("HttpIgnoreBody")

        override fun prepare(block: Unit.() -> Unit): HttpIgnoreBody = HttpIgnoreBody()

        override fun install(feature: HttpIgnoreBody, scope: HttpClient) {
            scope.responsePipeline.intercept(HttpResponsePipeline.Transform) { data ->
                if (data.expectedType != Unit::class) return@intercept
                data.response.close()
                data.response.body = Unit
            }
        }
    }
}