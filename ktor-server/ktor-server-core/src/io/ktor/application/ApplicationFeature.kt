package io.ktor.application

import io.ktor.pipeline.*
import io.ktor.util.*

@Suppress("AddVarianceModifier")
interface ApplicationFeature<in TPipeline : Pipeline<*, ApplicationCall>, out TBuilder : Any, TFeature : Any> {
    /**
     * Unique key that identifies a feature
     */
    val key: AttributeKey<TFeature>

    /**
     * Feature installation script
     */
    fun install(pipeline: TPipeline, configure: TBuilder.() -> Unit): TFeature
}

private val featureRegistryKey = AttributeKey<Attributes>("ApplicationFeatureRegistry")

/**
 * Gets feature instance for this pipeline
 */
fun <A : Pipeline<*, ApplicationCall>, B : Any, F : Any> A.feature(feature: ApplicationFeature<A, B, F>): F {
    return attributes[featureRegistryKey][feature.key]
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
fun <P : Pipeline<*, ApplicationCall>, B : Any, F : Any> P.install(feature: ApplicationFeature<P, B, F>, configure: B.() -> Unit = {}): F {
    val registry = attributes.computeIfAbsent(featureRegistryKey) { Attributes() }
    val installedFeature = registry.getOrNull(feature.key)
    when (installedFeature) {
        null -> {
            try {
                @Suppress("DEPRECATION_ERROR")
                val installed = feature.install(this, configure)
                registry.put(feature.key, installed)
                //environment.log.trace("`${feature.name}` feature was installed successfully.")
                return installed
            } catch(t: Throwable) {
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
    val registry = attributes.computeIfAbsent(featureRegistryKey) { Attributes() }
    registry.allKeys.forEach {
        @Suppress("UNCHECKED_CAST")
        uninstallFeature(it as AttributeKey<Any>)
    }
}

/**
 * Uninstalls [feature] from the pipeline
 */
fun <A : Pipeline<*, ApplicationCall>, B : Any, F : Any> A.uninstall(feature: ApplicationFeature<A, B, F>) = uninstallFeature(feature.key)

/**
 * Uninstalls feature specified by [key] from the pipeline
 */
fun <A : Pipeline<*, ApplicationCall>, F : Any> A.uninstallFeature(key: AttributeKey<F>) {
    val registry = attributes.getOrNull(featureRegistryKey) ?: return
    val instance = registry.getOrNull(key) ?: return
    if (instance is AutoCloseable)
        instance.close()
    registry.remove(key)
}

class DuplicateApplicationFeatureException(message: String) : Exception(message)
