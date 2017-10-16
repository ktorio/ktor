package io.ktor.client.pipeline

import io.ktor.client.features.FEATURE_INSTALLED_LIST
import io.ktor.client.features.HttpClientFeature
import io.ktor.client.features.HttpIgnoreBody
import io.ktor.client.features.HttpPlainText
import io.ktor.util.AttributeKey
import io.ktor.util.Attributes


private val CLIENT_CONFIG_KEY = AttributeKey<ClientConfig>("ClientConfig")

class ClientConfig(private val parent: HttpClientScope) {
    private val features = mutableMapOf<AttributeKey<*>, (HttpClientScope) -> Unit>()
    private val customInterceptors = mutableMapOf<String, (HttpClientScope) -> Unit>()

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

    fun install(key: String, block: HttpClientScope.() -> Unit) {
        customInterceptors[key] = block
    }

    fun build(): HttpClientScope {
        val scope = HttpCallScope(parent)
        scope.attributes.put(CLIENT_CONFIG_KEY, this)

        features.values.forEach { scope.apply(it) }
        customInterceptors.values.forEach { scope.apply(it) }

        return scope
    }

    fun clone(parent: HttpClientScope): ClientConfig {
        val result = ClientConfig(parent)
        result.features.putAll(features)
        result.customInterceptors.putAll(customInterceptors)

        return result
    }
}

fun HttpClientScope.config(block: ClientConfig.() -> Unit): HttpClientScope {
    val config = attributes.computeIfAbsent(CLIENT_CONFIG_KEY) { ClientConfig(this) }
    return config.clone(this).apply(block).build()
}

fun HttpClientScope.default(features: List<HttpClientFeature<Any, out Any>> = listOf(HttpPlainText, HttpIgnoreBody)) = config {
    features.forEach { install(it) }
}
