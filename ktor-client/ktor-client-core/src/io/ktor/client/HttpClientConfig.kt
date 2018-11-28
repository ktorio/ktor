package io.ktor.client

import io.ktor.client.engine.*
import io.ktor.client.features.*
import io.ktor.util.*
import kotlin.collections.set

/**
 * Mutable configuration used by [HttpClient].
 */
@HttpClientDsl
class HttpClientConfig<T : HttpClientEngineConfig> {
    private val features = mutableMapOf<AttributeKey<*>, (HttpClient) -> Unit>()
    private val customInterceptors = mutableMapOf<String, (HttpClient) -> Unit>()

    internal var engineConfig: T.()->Unit = {}

    /**
     * Configure engine parameters.
     */
    fun engine(block: T.() -> Unit) {
        val oldConfig = engineConfig
        engineConfig = {
            oldConfig()
            block()
        }
    }

    /**
     * Use [HttpRedirect] feature to automatically follow redirects.
     */
    var followRedirects: Boolean = true

    /**
     * Use [defaultTransformers] to automatically handle simple [ContentType].
     */
    var useDefaultTransformers: Boolean = true

    /**
     * Terminate [HttpClient.responsePipeline] if status code is not success(>=300).
     */
    var expectSuccess: Boolean = true

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
    fun clone(): HttpClientConfig<T> {
        val result = HttpClientConfig<T>()
        result.features.putAll(features)
        result.customInterceptors.putAll(customInterceptors)
        result.engineConfig = engineConfig

        return result
    }

    /**
     * Install features from [other] client config.
     */
    operator fun plusAssign(other: HttpClientConfig<out T>) {
        followRedirects = other.followRedirects
        useDefaultTransformers = other.useDefaultTransformers
        expectSuccess = other.expectSuccess

        features += other.features
        customInterceptors += other.customInterceptors
    }
}

/**
 * Dsl marker for [HttpClient] dsl.
 */
@DslMarker
annotation class HttpClientDsl()
