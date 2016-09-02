package org.jetbrains.ktor.features

import org.jetbrains.ktor.pipeline.*
import org.jetbrains.ktor.util.*

interface ApplicationFeature<in A : Pipeline<*>, T : Any> {
    val key: AttributeKey<T>

    fun install(pipeline: A, configure: T.() -> Unit): T
    fun dispose() {
    }
}

fun <A : Pipeline<*>, T : Any> A.feature(feature: ApplicationFeature<A, T>) = attributes[feature.key]

fun <A : Pipeline<*>, T : Any> A.install(feature: ApplicationFeature<A, T>, configure: T.() -> Unit = {}): T {
    val installedFeature = attributes.getOrNull(feature.key)
    when (installedFeature) {
        null -> {
            try {
                val installed = feature.install(this, configure)
                attributes.put(feature.key, installed)
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
