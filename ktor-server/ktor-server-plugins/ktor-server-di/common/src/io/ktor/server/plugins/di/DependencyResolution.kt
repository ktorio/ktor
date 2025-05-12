/*
 * Copyright 2014-2025 JetBrains s.r.o and contributors. Use of this source code is governed by the Apache 2.0 license.
 */

package io.ktor.server.plugins.di

import io.ktor.server.config.ApplicationConfig
import io.ktor.server.config.SerializableConfigValue
import io.ktor.util.reflect.*
import kotlin.reflect.KProperty

/**
 * Functional interface for generating a fresh `DependencyResolver`.
 */
public fun interface DependencyResolution {
    /**
     * Resolves and creates a new instance of `DependencyResolver` using the provided `DependencyProvider`
     * and `DependencyReflection`.
     *
     * @param provider The `DependencyProvider` instance responsible for managing dependency initializers
     *                 and declarations.
     * @param external A `DependencyMap` of externally provided dependencies available during resolution.
     * @param reflection The `DependencyReflection` instance used for reflective creation of dependency
     *                   instances.
     * @return A new instance of `DependencyResolver` configured with the provided arguments
     */
    public fun resolve(
        provider: DependencyProvider,
        external: DependencyMap,
        reflection: DependencyReflection,
    ): DependencyResolver
}

/**
 * A map of object instances.
 */
public interface DependencyMap {
    public companion object {
        /**
         * A predefined, immutable, and empty implementation of the `DependencyMap` interface.
         *
         * This object does not contain any dependencies and will always return `false` when checked
         * for a dependency's presence using the `contains` method. Attempting to retrieve a dependency
         * using the `get` method of this object will always throw a `MissingDependencyException`.
         *
         * Use this object as a default or placeholder implementation.
         */
        public val EMPTY: DependencyMap = object : DependencyMap {
            override fun contains(key: DependencyKey): Boolean = false
            override fun <T> get(key: DependencyKey): T {
                throw MissingDependencyException(key)
            }
        }
    }

    /**
     * Checks if the given dependency key is present in the dependency map.
     *
     * @param key The unique key that identifies the dependency to check.
     * @return `true` if the dependency identified by the key is present in the map; otherwise `false`
     */
    public fun contains(key: DependencyKey): Boolean

    /**
     * Retrieves an instance of the dependency associated with the given key from the dependency map.
     *
     * @param key the unique key that identifies the dependency to retrieve
     * @return the instance of the dependency associated with the given key
     * @throws MissingDependencyException if no dependency is associated with the given key
     */
    public fun <T> get(key: DependencyKey): T
}

/**
 * A mutable extension of [DependencyMap] that allows for adding and retrieving dependencies.
 */
public interface MutableDependencyMap : DependencyMap {
    public companion object {
        /**
         * Converts a [DependencyMap] into a [DependencyResolver], combining the functionality of both.
         *
         * @param reflection an instance of [DependencyReflection] that provides the ability to create new instances
         *                   of dependencies using class references and initialization logic.
         * @return a new instance of [DependencyResolver] that combines the map behavior of [DependencyMap] with
         *         the instance creation and reflection capabilities of [DependencyReflection].
         */
        public fun MutableDependencyMap.asResolver(reflection: DependencyReflection): DependencyResolver =
            this as? DependencyResolver ?: object : MutableDependencyMap by this, DependencyResolver {
                override val reflection: DependencyReflection
                    get() = reflection
            }
    }

    /**
     * Retrieves the value associated with the specified key if it exists. If the key does not already have an associated
     * value, the result of invoking the [defaultValue] function will be stored and returned as the value for the given key.
     *
     * @param key the dependency key used to look up or store the value.
     * @param defaultValue a lambda function that provides a default value to store and return if the key is not found.
     * @return the value associated with the key, either retrieved from the existing association or newly computed and stored.
     */
    public fun <T> getOrPut(key: DependencyKey, defaultValue: () -> T): T
}

/**
 * Extends [DependencyMap] with reflection, allowing for the automatic injection of types.
 */
public interface DependencyResolver : MutableDependencyMap {
    public val reflection: DependencyReflection

    /**
     * Decorates the dependency resolver with a qualified name for the expected type.
     *
     * Useful with delegation when used like: `val connection by dependencies.named("postgres")`
     */
    public fun named(key: String): DependencyResolverContext =
        DependencyResolverContext(this, key)
}

/**
 * A default implementation of the [MutableDependencyMap] interface.
 *
 * It includes the basic operations by delegating to an internal [MutableMap],
 * and also supports an external fallback when no key is present.
 *
 * @constructor Creates a new instance of [DependencyMapImpl].
 * @param instances an initial map of dependencies, with keys as [DependencyKey] and values as [Result] wrapping the dependencies.
 * @param external an optional [DependencyMap] that serves as an external source for dependencies not found in the internal map.
 */
@Suppress("UNCHECKED_CAST")
public class DependencyMapImpl(
    instances: Map<DependencyKey, Result<*>>,
    private val external: DependencyMap = DependencyMap.EMPTY,
) : MutableDependencyMap {
    private val map = instances.toMutableMap()

    override fun contains(key: DependencyKey): Boolean =
        map.containsKey(key) || external.contains(key)

    override fun <T> get(key: DependencyKey): T {
        val result = map[key] ?: getExternal(key)
        val actual = result?.getOrThrow()
        return when {
            actual != null -> actual as T
            key.isNullable() -> null as T
            else -> throw MissingDependencyException(key)
        }
    }

    override fun <T> getOrPut(key: DependencyKey, defaultValue: () -> T): T =
        map.getOrPut(key) { runCatching(defaultValue) }.getOrThrow() as T

    private fun getExternal(key: DependencyKey): Result<Any>? =
        if (external.contains(key)) {
            Result.success(external.get(key))
        } else {
            null
        }
}

/**
 * Combines two `DependencyMap`s into one.
 *
 * Where keys are common, precedence is given to the right-hand argument.
 *
 * @param right The DependencyMap to merge with.
 * @return A new DependencyMap instance that contains the keys of both.
 */
public operator fun DependencyMap.plus(right: DependencyMap): DependencyMap =
    MergedDependencyMap(this, right)

/**
 * Get the dependency from the map for the key represented by the type (and optionally, with the given name).
 */
public inline fun <reified T> DependencyMap.resolve(key: String? = null): T =
    get(dependencyKey<T>(key))

internal class MergedDependencyMap(
    private val left: DependencyMap,
    private val right: DependencyMap,
) : DependencyMap {
    override fun contains(key: DependencyKey): Boolean =
        right.contains(key) || left.contains(key)

    override fun <T> get(key: DependencyKey): T =
        if (right.contains(key)) {
            right.get(key)
        } else {
            left.get(key)
        }
}

/**
 * Qualifier for specifying when a dependency key maps to a property in the file configuration.
 */
public data object PropertyQualifier

/**
 * Implementation of [DependencyMap] for referencing items from the server's file configuration.
 */
@Suppress("UNCHECKED_CAST")
public class ConfigurationDependencyMap(
    private val config: ApplicationConfig,
) : DependencyMap {
    override fun contains(key: DependencyKey): Boolean =
        key.qualifier == PropertyQualifier && key.name != null && config.propertyOrNull(key.name) != null

    override fun <T> get(key: DependencyKey): T =
        if (key.qualifier != PropertyQualifier || key.name == null) {
            throw MissingDependencyException(key)
        } else {
            (config.propertyOrNull(key.name) as? SerializableConfigValue)?.getAs(key.type) as? T
                ?: throw MissingDependencyException(key)
        }
}

/**
 * Context for property delegation with chaining (i.e., `dependencies.named("foo")`)
 */
public data class DependencyResolverContext(
    val resolver: DependencyResolver,
    val name: String,
) {
    /**
     * Property delegation for [DependencyResolverContext] for use with the `named` shorthand for string qualifiers.
     */
    public inline operator fun <reified T> getValue(thisRef: Any?, property: KProperty<*>): T =
        resolver.resolve(name)

    /**
     * Get the dependency from the map for the key represented by the type (and optionally, with the given name).
     */
    public inline fun <reified T> DependencyMap.resolve(key: String? = null): T =
        get(dependencyKey<T>(key))
}
