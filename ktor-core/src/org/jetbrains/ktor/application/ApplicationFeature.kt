package org.jetbrains.ktor.application

import org.jetbrains.ktor.pipeline.*
import org.jetbrains.ktor.util.*

interface ApplicationFeature<in P : Pipeline<*>, out B : Any, F : Any> {
    /**
     * Unique key that identifies a feature
     */
    val key: AttributeKey<F>

    /**
     * Feature installation script
     */
    @Deprecated("This method cannot be called directly", ReplaceWith("pipeline.install(configure)"), DeprecationLevel.ERROR)
    fun install(pipeline: P, configure: B.() -> Unit): F

    companion object {
        val registry = AttributeKey<Attributes>("ApplicationRegistry")
    }
}

/**
 * Gets feature instance for this pipeline, if any
 */
fun <A : Pipeline<*>, B : Any, F : Any> A.feature(feature: ApplicationFeature<A, B, F>): F = attributes[ApplicationFeature.registry][feature.key]

/**
 * Installs [feature] into this pipeline, if it is not yet installed
 */
fun <P : Pipeline<*>, B : Any, F : Any> P.install(feature: ApplicationFeature<P, B, F>, configure: B.() -> Unit = {}): F {
    val registry = attributes.computeIfAbsent(ApplicationFeature.registry) { Attributes() }
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
fun <A : Pipeline<*>> A.uninstallAllFeatures() {
    val registry = attributes.computeIfAbsent(ApplicationFeature.registry) { Attributes() }
    registry.allKeys.forEach { uninstallFeature(it as AttributeKey<Any>) }
}

/**
 * Uninstalls [feature] from the pipeline
 */
fun <A : Pipeline<*>, B : Any, F : Any> A.uninstall(feature: ApplicationFeature<A, B, F>) = uninstallFeature(feature.key)

/**
 * Uninstalls feature specified by [key] from the pipeline
 */
fun <A : Pipeline<*>, F : Any> A.uninstallFeature(key: AttributeKey<F>) {
    val registry = attributes.getOrNull(ApplicationFeature.registry) ?: return
    val instance = registry.getOrNull(key) ?: return
    if (instance is AutoCloseable)
        instance.close()
    registry.remove(key)
}

class DuplicateApplicationFeatureException(message: String) : Exception(message)
