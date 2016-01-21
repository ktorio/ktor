package org.jetbrains.ktor.features

import org.jetbrains.ktor.application.*
import org.jetbrains.ktor.util.*

interface ApplicationFeature<T : Any> {
    val name: String
    val key: AttributeKey<T>

    fun install(application: Application, configure: T.() -> Unit): T
}

fun <T : Any> Application.feature(feature: ApplicationFeature<T>) = attributes[feature.key]

fun <T : Any> Application.install(feature: ApplicationFeature<T>, configure: T.() -> Unit = {}): T {
    val installedFeature = attributes.getOrNull(feature.key)
    when (installedFeature) {
        null -> {
            try {
                val installed = feature.install(this, configure)
                attributes.put(feature.key, installed)
                config.log.info("`${feature.name}` feature was installed successfully.")
                return installed
            } catch(t: Throwable) {
                config.log.error("`${feature.name}` feature failed to install.", t)
                throw t
            }
        }
        feature -> {
            config.log.warning("`${feature.name}` feature is already installed")
            return installedFeature
        }
        else -> {
            throw DuplicateApplicationFeatureException("Conflicting application feature is already installed with the same key as `${feature.name}`")
        }
    }
}

class DuplicateApplicationFeatureException(message: String) : Exception(message)
