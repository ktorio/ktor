package io.ktor.client

import io.ktor.client.features.*
import io.ktor.util.*
import kotlin.collections.set

/**
 * Mutable configuration used by [HttpClient].
 */
class HttpClientConfig {
    private val features = mutableMapOf<AttributeKey<*>, (HttpClient) -> Unit>()
    private val customInterceptors = mutableMapOf<String, (HttpClient) -> Unit>()

    /**
     * Installs a specific [feature] and optionally [configure] it.
     */
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

    /**
     * Installs an interceptor defined by [block].
     * The [key] parameter is used as a unique name, that also prevents installing duplicated interceptors.
     */
    fun install(key: String, block: HttpClient.() -> Unit) {
        customInterceptors[key] = block
    }

    /**
     * Applies all the installed [features] and [customInterceptors] from this configuration
     * into the specified [client].
     */
    fun install(client: HttpClient) {
        features.values.forEach { client.apply(it) }
        customInterceptors.values.forEach { client.apply(it) }
    }

    /**
     * Clones this [HttpClientConfig] duplicating all the [features] and [customInterceptors].
     */
    fun clone(): HttpClientConfig {
        val result = HttpClientConfig()
        result.features.putAll(features)
        result.customInterceptors.putAll(customInterceptors)

        return result
    }
}
