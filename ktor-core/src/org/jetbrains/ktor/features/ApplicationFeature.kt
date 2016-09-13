package org.jetbrains.ktor.features

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

class DuplicateApplicationFeatureException(message: String) : Exception(message)
