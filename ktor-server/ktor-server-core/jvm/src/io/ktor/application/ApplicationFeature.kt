/*
 * Copyright 2014-2019 JetBrains s.r.o and contributors. Use of this source code is governed by the Apache 2.0 license.
 */

package io.ktor.application

import io.ktor.util.pipeline.*
import io.ktor.util.*
import kotlinx.coroutines.*
import io.ktor.utils.io.core.*

/**
 * Defines an installable Application Feature
 * @param TPipeline is the type of the pipeline this feature is compatible with
 * @param TConfiguration is the type for the configuration object for this Feature
 * @param TFeature is the type for the instance of the Feature object
 */
@Suppress("AddVarianceModifier")
interface ApplicationFeature<in TPipeline : Pipeline<*, ApplicationCall>, out TConfiguration : Any, TFeature : Any> {
    /**
     * Unique key that identifies a feature
     */
    val key: AttributeKey<TFeature>

    /**
     * Feature installation script
     */
    fun install(pipeline: TPipeline, configure: TConfiguration.() -> Unit): TFeature
}

private val featureRegistryKey = AttributeKey<Attributes>("ApplicationFeatureRegistry")

/**
 * Gets feature instance for this pipeline, or fails with [MissingApplicationFeatureException] if the feature is not installed
 * @throws MissingApplicationFeatureException
 * @param feature application feature to lookup
 * @return an instance of feature
 */
fun <A : Pipeline<*, ApplicationCall>, B : Any, F : Any> A.feature(feature: ApplicationFeature<A, B, F>): F {
    return attributes[featureRegistryKey].getOrNull(feature.key)
            ?: throw MissingApplicationFeatureException(feature.key)
}

/**
 * Returns feature instance for this pipeline, or null if feature is not installed
 */
fun <A : Pipeline<*, ApplicationCall>, B : Any, F : Any> A.featureOrNull(feature: ApplicationFeature<A, B, F>): F? {
    return attributes.getOrNull(featureRegistryKey)?.getOrNull(feature.key)
}

/**
 * Installs [feature] into this pipeline, if it is not yet installed
 */
fun <P : Pipeline<*, ApplicationCall>, B : Any, F : Any> P.install(
    feature: ApplicationFeature<P, B, F>,
    configure: B.() -> Unit = {}
): F {
    val registry = attributes.computeIfAbsent(featureRegistryKey) { Attributes(true) }
    val installedFeature = registry.getOrNull(feature.key)
    when (installedFeature) {
        null -> {
            try {
                @Suppress("DEPRECATION_ERROR")
                val installed = feature.install(this, configure)
                registry.put(feature.key, installed)
                //environment.log.trace("`${feature.name}` feature was installed successfully.")
                return installed
            } catch (t: Throwable) {
                //environment.log.error("`${feature.name}` feature failed to install.", t)
                throw t
            }
        }
        feature -> {
            //environment.log.warning("`${feature.name}` feature is already installed")
            return installedFeature
        }
        else -> {
            throw DuplicateApplicationFeatureException("Conflicting application feature is already installed with the same key as `${feature.key.name}`")
        }
    }
}

/**
 * Uninstalls all features from the pipeline
 */
fun <A : Pipeline<*, ApplicationCall>> A.uninstallAllFeatures() {
    val registry = attributes.computeIfAbsent(featureRegistryKey) { Attributes(true) }
    registry.allKeys.forEach {
        @Suppress("UNCHECKED_CAST")
        uninstallFeature(it as AttributeKey<Any>)
    }
}

/**
 * Uninstalls [feature] from the pipeline
 */
fun <A : Pipeline<*, ApplicationCall>, B : Any, F : Any> A.uninstall(feature: ApplicationFeature<A, B, F>) =
    uninstallFeature(feature.key)

/**
 * Uninstalls feature specified by [key] from the pipeline
 */
fun <A : Pipeline<*, ApplicationCall>, F : Any> A.uninstallFeature(key: AttributeKey<F>) {
    val registry = attributes.getOrNull(featureRegistryKey) ?: return
    val instance = registry.getOrNull(key) ?: return
    if (instance is Closeable)
        instance.close()
    registry.remove(key)
}

/**
 * Thrown when Application Feature has been attempted to be installed with the same key as already installed Feature
 */
class DuplicateApplicationFeatureException(message: String) : Exception(message)

/**
 * Thrown when Application Feature has been attempted to be accessed but has not been installed before
 * @param key application feature's attribute key
 */
class MissingApplicationFeatureException(
    val key: AttributeKey<*>
) : IllegalStateException(), CopyableThrowable<MissingApplicationFeatureException> {
    override val message: String get() = "Application feature ${key.name} is not installed"

    override fun createCopy(): MissingApplicationFeatureException? = MissingApplicationFeatureException(key).also {
        it.initCause(this)
    }
}
