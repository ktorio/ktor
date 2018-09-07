package io.ktor.client.features

import io.ktor.client.*
import io.ktor.client.request.*
import io.ktor.util.*


/**
 * [Feature] is used to set request default parameters.
 */
class DefaultRequest(private val builder: HttpRequestBuilder.() -> Unit) {

    companion object Feature : HttpClientFeature<HttpRequestBuilder, DefaultRequest> {
        override val key: AttributeKey<DefaultRequest> = AttributeKey("DefaultRequest")

        override fun prepare(block: HttpRequestBuilder.() -> Unit): DefaultRequest =
            DefaultRequest(block)

        override fun install(feature: DefaultRequest, scope: HttpClient) {
            scope.requestPipeline.intercept(HttpRequestPipeline.Before) {
                context.apply(feature.builder)
            }
        }
    }

}

/**
 * Set request default parameters.
 */
fun HttpClientConfig<*>.defaultRequest(block: HttpRequestBuilder.() -> Unit) {
    install(DefaultRequest) {
        block()
    }
}
