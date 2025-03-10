/*
 * Copyright 2014-2025 JetBrains s.r.o and contributors. Use of this source code is governed by the Apache 2.0 license.
 */

package io.ktor.server.plugins.di

import io.ktor.server.application.ApplicationPlugin
import io.ktor.server.application.createApplicationPlugin
import io.ktor.util.AttributeKey
import io.ktor.util.reflect.TypeInfo

/**
 * A Ktor application plugin for managing the registration and resolution of module dependencies.
 *
 * The following properties are configurable:
 * 1. `provider`: the logic for registering new types
 * 2. `resolution`: the function for validating the provided types and creating the dependency map
 * 3. `reflection`: controls how objects are initialized from type references
 */
public val DI: ApplicationPlugin<DependencyInjectionConfig> =
    createApplicationPlugin("DI", ::DependencyInjectionConfig) {
        application.attributes.put(
            DependencyRegistryKey,
            DependencyRegistryImpl(
                pluginConfig.provider,
                pluginConfig.resolution,
                pluginConfig.reflection,
            )
        )
    }

public val DependencyRegistryKey: AttributeKey<DependencyRegistry> =
    AttributeKey<DependencyRegistry>("DependencyRegistry")

public expect val DefaultReflection: DependencyReflection

public class DependencyInjectionConfig {
    public var reflection: DependencyReflection = DefaultReflection
    public var provider: DependencyProvider = MapDependencyProvider()
    public var resolution: DependencyResolution = DefaultDependencyResolution

    public fun provider(config: ProviderScope.() -> Unit) {
        provider = ProviderScope().apply(config).let { (keyMapping, conflictPolicy, onConflict) ->
            MapDependencyProvider(keyMapping, conflictPolicy, onConflict)
        }
    }

    public data class ProviderScope(
        public var keyMapping: DependencyKeyCovariance = Supertypes,
        public var conflictPolicy: DependencyConflictPolicy = DefaultConflictPolicy,
        public var onConflict: (DependencyKey) -> Unit = { throw DuplicateDependencyException(it) }
    )
}

/**
 * Unique key for a dependency.
 */
public data class DependencyKey(val type: TypeInfo, val name: String? = null) {
    override fun toString(): String = buildString {
        append(type.kotlinType ?: type.type)
        if (name != null) {
            append("(name = \"$name\")")
        }
    }
}

/**
 * Thrown when attempting to resolve a dependency that was not declared.
 */
public class MissingDependencyException(key: DependencyKey) :
    IllegalArgumentException("Could not resolve dependency for `$key`")

/**
 * Thrown when a dependency is declared more than once.
 */
public class DuplicateDependencyException(key: DependencyKey) :
    IllegalArgumentException("Attempted to redefine dependency `$key`")

/**
 * Thrown when there are two or more implicit dependencies that match the given key.
 */
public class AmbiguousDependencyException(key: DependencyKey, keys: Collection<DependencyKey>) :
    IllegalArgumentException("Cannot decide which value for $key. Possible implementations: $keys")

/**
 * Thrown when resolving a given dependency loops back on itself.
 */
public class CircularDependencyException(key: DependencyKey) :
    IllegalStateException("Circular dependency found for dependency `$key`")

/**
 * Thrown when attempting to provide a dependency AFTER the dependency map is created.
 */
public class OutOfOrderDependencyException(key: DependencyKey) :
    IllegalStateException("Attempted to define $key after dependencies were resolved")
