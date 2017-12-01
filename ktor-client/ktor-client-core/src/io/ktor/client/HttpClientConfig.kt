package io.ktor.client

import io.ktor.client.features.*
import io.ktor.util.*
import kotlin.collections.set


class HttpClientConfig {
    private val features = mutableMapOf<AttributeKey<*>, (HttpClient) -> Unit>()
    private val customInterceptors = mutableMapOf<String, (HttpClient) -> Unit>()

    fun <TBuilder : Any, TFeature : Any> install(
            feature: HttpClientFeature<TBuilder, TFeature>,
            configure: TBuilder.() -> Unit = {}
    ) {
        val featureData = feature.prepare(configure)

        features[feature.key] = { scope ->
            val attributes = scope.attributes.computeIfAbsent(FEATURE_INSTALLED_LIST) { Attributes() }

            feature.install(featureData, scope)
            attributes.put(feature.key, featureData)
        }
    }

    fun install(key: String, block: HttpClient.() -> Unit) {
        customInterceptors[key] = block
    }

    fun install(client: HttpClient) {
        features.values.forEach { client.apply(it) }
        customInterceptors.values.forEach { client.apply(it) }
    }

    fun clone(): HttpClientConfig {
        val result = HttpClientConfig()
        result.features.putAll(features)
        result.customInterceptors.putAll(customInterceptors)

        return result
    }
}
