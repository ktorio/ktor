package io.ktor.client.features

import io.ktor.client.*
import io.ktor.util.*


internal val FEATURE_INSTALLED_LIST = AttributeKey<Attributes>("ApplicationFeatureRegistry")

interface HttpClientFeature<out TBuilder : Any, TFeature : Any> {
    val key: AttributeKey<TFeature>

    fun prepare(block: TBuilder.() -> Unit): TFeature

    fun install(feature: TFeature, scope: HttpClient)
}

fun <B : Any, F : Any> HttpClient.feature(feature: HttpClientFeature<B, F>): F? =
        attributes.getOrNull(FEATURE_INSTALLED_LIST)?.getOrNull(feature.key)
