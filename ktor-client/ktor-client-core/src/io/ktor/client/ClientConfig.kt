package io.ktor.client

import io.ktor.client.features.*
import io.ktor.util.*
import java.io.*
import kotlin.collections.set


private val CLIENT_CONFIG_KEY = AttributeKey<ClientConfig>("ClientConfig")

class ClientConfig(private val parent: Closeable) {
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

    fun build(): HttpClient {
        val scope = HttpCallScope(parent)
        scope.attributes.put(CLIENT_CONFIG_KEY, this)

        features.values.forEach { scope.apply(it) }
        customInterceptors.values.forEach { scope.apply(it) }

        return scope
    }

    fun clone(parent: HttpClient): ClientConfig {
        val result = ClientConfig(parent)
        result.features.putAll(features)
        result.customInterceptors.putAll(customInterceptors)

        return result
    }
}

internal fun HttpClient.config(block: ClientConfig.() -> Unit): HttpClient {
    val config = attributes.computeIfAbsent(CLIENT_CONFIG_KEY) { ClientConfig(this) }
    return config.clone(this).apply(block).build()
}

internal fun HttpClient.default(features: List<HttpClientFeature<Any, out Any>> = listOf(HttpPlainText, HttpIgnoreBody)) = config {
    features.forEach { install(it) }
}
