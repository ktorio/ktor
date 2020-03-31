/*
 * Copyright 2014-2019 JetBrains s.r.o and contributors. Use of this source code is governed by the Apache 2.0 license.
 */

package io.ktor.client.features

import io.ktor.client.*
import io.ktor.util.*
import kotlin.native.concurrent.*

@SharedImmutable
internal val FEATURE_INSTALLED_LIST = AttributeKey<Attributes>("ApplicationFeatureRegistry")

/**
 * Base interface representing a [HttpClient] feature.
 */
public interface HttpClientFeature<out TConfig : Any, TFeature : Any> {
    /**
     * The [AttributeKey] for this feature.
     */
    public val key: AttributeKey<TFeature>

    /**
     * Builds a [TFeature] by calling the [block] with a [TConfig] config instance as receiver.
     */
    public fun prepare(block: TConfig.() -> Unit = {}): TFeature

    /**
     * Installs the [feature] class for a [HttpClient] defined at [scope].
     */
    public fun install(feature: TFeature, scope: HttpClient)
}

/**
 * Try to get the [feature] installed in this client. Returns `null` if the feature was not previously installed.
 */
public fun <B : Any, F : Any> HttpClient.feature(feature: HttpClientFeature<B, F>): F? =
    attributes.getOrNull(FEATURE_INSTALLED_LIST)?.getOrNull(feature.key)

/**
 * Find the [feature] installed in [HttpClient].
 *
 * @throws [IllegalStateException] if [feature] is not installed.
 */
public operator fun <B : Any, F: Any> HttpClient.get(feature: HttpClientFeature<B, F>): F {
    val message = "Feature $feature is not installed. Consider using `install(${feature.key})` in client config first."
    return feature(feature) ?: error(message)
}
