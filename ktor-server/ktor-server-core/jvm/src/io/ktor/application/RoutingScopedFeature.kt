/*
 * Copyright 2014-2020 JetBrains s.r.o and contributors. Use of this source code is governed by the Apache 2.0 license.
 */

package io.ktor.application

import io.ktor.routing.*
import io.ktor.util.*
import io.ktor.util.pipeline.*

/**
 * Gets feature instance for this pipeline, or fails with [MissingApplicationFeatureException] if the feature is not installed
 * @throws MissingApplicationFeatureException
 * @param feature application feature to lookup
 * @return an instance of feature
 */
public fun <A : Pipeline<*, ApplicationCall>, B : Any, F : Any> A.feature(feature: RoutingScopedFeature<A, B, F>): F {
    return findFeatureInRoute(feature) ?: throw MissingApplicationFeatureException(feature.key)
}

/**
 * Defines an installable Application Feature that can be installed and configured on subroutes
 * @param TPipeline is the type of the pipeline this feature is compatible with
 * @param TConfiguration is the type for the configuration object for this Feature
 * @param TFeature is the type for the instance of the Feature object
 */
public interface RoutingScopedFeature<
    in TPipeline : Pipeline<*, ApplicationCall>, TConfiguration : Any, TFeature : Any> :
    ApplicationFeature<TPipeline, TConfiguration, TFeature> {

    /**
     * Unique key that identifies a feature configuration
     */
    public val configKey: AttributeKey<TConfiguration.() -> Unit>
        get() = EquatableAttributeKey("${key.name}_configBuilder")

    @Deprecated(
        "This feature can be installed multiple times in routing with different configs. " +
            "To get actual config for current route use `configurationBlock` property inside call interceptor.",
        replaceWith = ReplaceWith("install"),
        level = DeprecationLevel.ERROR
    )
    public override fun install(pipeline: TPipeline, configure: TConfiguration.() -> Unit): TFeature {
        return install(pipeline)
    }

    /**
     * Feature installation script
     */
    public fun install(pipeline: TPipeline): TFeature

    /**
     * Block that initializes [TConfiguration] inside call interceptor
     */
    public val PipelineContext<*, ApplicationCall>.configurationBlock: (TConfiguration.() -> Unit)
        get() = call.attributes[configKey]
}

/**
 * Installs [feature] into this pipeline, if it is not yet installed
 */
public fun <P : Pipeline<*, ApplicationCall>, B : Any, F : Any> P.install(
    feature: RoutingScopedFeature<P, B, F>,
    configure: B.() -> Unit = {}
): F {
    intercept(ApplicationCallPipeline.Setup) {
        call.attributes.put(feature.configKey, configure)
    }

    val installedFeature = findFeatureInRoute(feature)
    if (installedFeature != null) {
        return installedFeature
    }

    // dynamic feature needs to be installed into routing, because only routing will have all interceptors
    @Suppress("UNCHECKED_CAST")
    val installPipeline = when (this) {
        is Application -> routing {} as P
        else -> this
    }
    val installed = feature.install(installPipeline)
    val registry = installPipeline.featureRegistry
    registry.put(feature.key, installed)
    return installed
}

private fun <P : Pipeline<*, ApplicationCall>, B : Any, F : Any> P.findFeatureInRoute(
    feature: RoutingScopedFeature<P, B, F>
): F? {
    var current: Route? = this as? Route
    while (current != null) {
        val registry = current.featureRegistry
        val installedFeature = registry.getOrNull(feature.key)
        if (installedFeature != null) {
            return installedFeature
        }
        current = current.parent
    }
    return null
}
