/*
 * Copyright 2014-2025 JetBrains s.r.o and contributors. Use of this source code is governed by the Apache 2.0 license.
 */

package io.ktor.server.plugins.di

import io.ktor.server.config.*

@Suppress("UNCHECKED_CAST")
public class ConfigurationResolver(private val config: ApplicationConfig): DependencyMap {
    override fun contains(key: DependencyKey): Boolean =
        key is PropertyKey && config.propertyOrNull(key.path) != null

    override fun <T : Any> get(key: DependencyKey): T =
        (key as? PropertyKey)?.let {
            (config.property(key.path) as? SerializableConfigValue)?.let { property ->
                property.getAs(key.type) as T
            }
        } ?: throw MissingDependencyException(key)

    override fun <T : Any> getOrPut(key: DependencyKey, defaultValue: () -> T): T =
        TODO()
}
