/*
 * Copyright 2014-2025 JetBrains s.r.o and contributors. Use of this source code is governed by the Apache 2.0 license.
 */

package io.ktor.server.plugins.di

import io.ktor.server.config.*
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Deferred
import kotlin.reflect.KProperty

/**
 * Functional interface for generating a fresh `DependencyResolver`.
 *
 * [Report a problem](https://ktor.io/feedback/?fqname=io.ktor.server.plugins.di.DependencyResolution)
 */
public fun interface DependencyResolution {
    /**
     * Resolves and creates a new instance of `DependencyResolver` using the provided `DependencyProvider`
     * and `DependencyReflection`.
     *
     * [Report a problem](https://ktor.io/feedback/?fqname=io.ktor.server.plugins.di.DependencyResolution.resolve)
     *
     * @param provider The `DependencyProvider` instance responsible for managing dependency initializers
     *                 and declarations.
     * @param external A `DependencyMap` of externally provided dependencies available during resolution.
     * @param reflection The `DependencyReflection` instance used for reflective creation of dependency
     *                   instances.
     * @return A new instance of `DependencyResolver` configured with the provided arguments
     */
    public fun CoroutineScope.resolve(
        provider: DependencyProvider,
        external: DependencyMap,
        reflection: DependencyReflection,
    ): DependencyResolver
}

/**
 * A map of object instances.
 *
 * [Report a problem](https://ktor.io/feedback/?fqname=io.ktor.server.plugins.di.DependencyMap)
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
         *
         * [Report a problem](https://ktor.io/feedback/?fqname=io.ktor.server.plugins.di.DependencyMap.Companion.EMPTY)
         */
        public val EMPTY: DependencyMap = object : DependencyMap {
            override fun contains(key: DependencyKey): Boolean = false
            override fun getInitializer(key: DependencyKey): DependencyInitializer =
                throw MissingDependencyException(key)
        }

        public fun fromMap(map: Map<DependencyKey, Any>): DependencyMap =
            fromLookup(map::get)

        @Suppress("UNCHECKED_CAST")
        public fun fromLookup(resolve: (DependencyKey) -> Any?): DependencyMap = object : DependencyMap {
            override fun contains(key: DependencyKey): Boolean = resolve(key) != null
            override fun getInitializer(key: DependencyKey): DependencyInitializer =
                DependencyInitializer.Value(key, resolve(key))
        }
    }

    /**
     * Checks if the given dependency key is present in the dependency map.
     *
     * [Report a problem](https://ktor.io/feedback/?fqname=io.ktor.server.plugins.di.DependencyMap.contains)
     *
     * @param key The unique key that identifies the dependency to check.
     * @return `true` if the dependency identified by the key is present in the map; otherwise `false`
     */
    public fun contains(key: DependencyKey): Boolean

    public fun getInitializer(key: DependencyKey): DependencyInitializer
}

/**
 * Get an item from the dependency map synchronously.
 *
 * Unavailable on WASM / JS targets.
 *
 * [Report a problem](https://ktor.io/feedback/?fqname=io.ktor.server.plugins.di.getBlocking)
 *
 * @param key the unique key that identifies the dependency to retrieve
 * @return the instance of the dependency associated with the given key
 * @throws MissingDependencyException if no dependency is associated with the given key
 */
public expect fun <T> DependencyResolver.getBlocking(key: DependencyKey): T

/**
 * A mutable extension of [DependencyMap] that allows for adding and retrieving dependencies.
 *
 * [Report a problem](https://ktor.io/feedback/?fqname=io.ktor.server.plugins.di.MutableDependencyMap)
 */
public interface MutableDependencyMap : DependencyMap {
    /**
     * Retrieves the value associated with the specified key if it exists. If the key does not already have an associated
     * value, the result of invoking the [defaultValue] function will be stored and returned as the value for the given key.
     *
     * [Report a problem](https://ktor.io/feedback/?fqname=io.ktor.server.plugins.di.MutableDependencyMap.getOrPut)
     *
     * @param key the dependency key used to look up or store the value.
     * @param defaultValue a lambda function that provides a default value to store and return if the key is not found.
     * @return the value associated with the key, either retrieved from the existing association or newly computed and stored.
     */
    public suspend fun <T> getOrPut(key: DependencyKey, defaultValue: suspend () -> T): T
}

/**
 * Extends [DependencyMap] with reflection, allowing for the automatic injection of types.
 *
 * [Report a problem](https://ktor.io/feedback/?fqname=io.ktor.server.plugins.di.DependencyResolver)
 */
public interface DependencyResolver : MutableDependencyMap, CoroutineScope {
    public val reflection: DependencyReflection

    /**
     * Decorates the dependency resolver with a qualified name for the expected type.
     *
     * Useful with delegation when used like: `val connection by dependencies.named("postgres")`
     *
     * [Report a problem](https://ktor.io/feedback/?fqname=io.ktor.server.plugins.di.DependencyResolver.named)
     */
    public fun named(key: String): DependencyResolverContext =
        DependencyResolverContext(this, key)

    @Suppress("UNCHECKED_CAST")
    public fun <T> getDeferred(key: DependencyKey): Deferred<T> =
        getInitializer(key).resolve(this) as Deferred<T>

    /**
     * Retrieves an instance of the dependency associated with the given key from the dependency map.
     *
     * [Report a problem](https://ktor.io/feedback/?fqname=io.ktor.server.plugins.di.DependencyResolver.get)
     *
     * @param key the unique key that identifies the dependency to retrieve
     * @return the instance of the dependency associated with the given key
     * @throws MissingDependencyException if no dependency is associated with the given key
     */
    public suspend fun <T> get(key: DependencyKey): T =
        getDeferred<T>(key).await()
}

@Suppress("UNCHECKED_CAST")
public class MapDependencyResolver(
    private val map: DependencyInitializerMap,
    private val extension: DependencyMap,
    override val reflection: DependencyReflection,
    private var waitForValues: Boolean = false,
    private val coroutineScope: CoroutineScope,
) : DependencyResolver, CoroutineScope by coroutineScope {

    /**
     * Updates the waitForValues flag so that future consumers will fail immediately when no initializer is found.
     *
     * This is called during application startup when all modules have either suspended or completed.
     *
     * [Report a problem](https://ktor.io/feedback/?fqname=io.ktor.server.plugins.di.MapDependencyResolver.stopWaiting)
     */
    public fun stopWaiting() {
        waitForValues = false

        // Cancel all pending deferred values
        map.values.filterIsInstance<DependencyInitializer.Missing>().forEach { placeholder ->
            placeholder.throwMissing()
        }
    }

    override fun contains(key: DependencyKey): Boolean =
        map.isProvided(key) || extension.contains(key)

    override fun getInitializer(key: DependencyKey): DependencyInitializer =
        map[key]
            ?: tryExternal(key)
            ?: tryNullable(key)
            ?: onMissing(key)

    override suspend fun <T> getOrPut(key: DependencyKey, defaultValue: suspend () -> T): T {
        val deferred = map.getOrPut(key) {
            DependencyInitializer.Explicit(key) {
                defaultValue()
            }
        }.resolve(this)

        return deferred.await() as T
    }

    private fun tryExternal(key: DependencyKey): DependencyInitializer? =
        if (extension.contains(key)) {
            extension.getInitializer(key)
        } else {
            null
        }

    private fun tryNullable(key: DependencyKey): DependencyInitializer? =
        if (key.isNullable()) {
            DependencyInitializer.Null(key)
        } else {
            null
        }

    private fun onMissing(key: DependencyKey): DependencyInitializer =
        if (waitForValues) {
            map.getOrPut(key) { DependencyInitializer.Missing(key, this) }
        } else {
            throw MissingDependencyException(key)
        }
}

/**
 * Combines two `DependencyMap`s into one.
 *
 * Where keys are common, precedence is given to the right-hand argument.
 *
 * [Report a problem](https://ktor.io/feedback/?fqname=io.ktor.server.plugins.di.plus)
 *
 * @param right The DependencyMap to merge with.
 * @return A new DependencyMap instance that contains the keys of both.
 */
public operator fun DependencyMap.plus(right: DependencyMap): DependencyMap =
    MergedDependencyMap(this, right)

/**
 * Get the dependency from the map for the key represented by the type (and optionally, with the given name).
 *
 * [Report a problem](https://ktor.io/feedback/?fqname=io.ktor.server.plugins.di.resolve)
 */
public suspend inline fun <reified T> DependencyResolver.resolve(key: String? = null): T =
    get(DependencyKey<T>(key))

internal class MergedDependencyMap(
    private val left: DependencyMap,
    private val right: DependencyMap,
) : DependencyMap {
    override fun contains(key: DependencyKey): Boolean =
        right.contains(key) || left.contains(key)

    override fun getInitializer(key: DependencyKey): DependencyInitializer {
        return if (right.contains(key)) {
            right.getInitializer(key)
        } else {
            left.getInitializer(key)
        }
    }
}

/**
 * Qualifier for specifying when a dependency key maps to a property in the file configuration.
 *
 * [Report a problem](https://ktor.io/feedback/?fqname=io.ktor.server.plugins.di.PropertyQualifier)
 */
public data object PropertyQualifier

/**
 * Implementation of [DependencyMap] for referencing items from the server's file configuration.
 *
 * [Report a problem](https://ktor.io/feedback/?fqname=io.ktor.server.plugins.di.ConfigurationDependencyMap)
 */
@Suppress("UNCHECKED_CAST")
public class ConfigurationDependencyMap(
    private val config: ApplicationConfig,
) : DependencyMap {
    override fun contains(key: DependencyKey): Boolean =
        key.qualifier == PropertyQualifier && key.name != null && config.propertyOrNull(key.name) != null

    override fun getInitializer(key: DependencyKey): DependencyInitializer =
        DependencyInitializer.Value(key, getPropertyValue(key))

    private fun getPropertyValue(key: DependencyKey): Any? =
        if (key.qualifier != PropertyQualifier || key.name == null) {
            throw MissingDependencyException(key)
        } else {
            config.propertyOrNull(key.name)?.getAs(key.type)
                ?: throw MissingDependencyException(key)
        }
}

/**
 * Context for property delegation with chaining (i.e., `dependencies.named("foo")`)
 *
 * [Report a problem](https://ktor.io/feedback/?fqname=io.ktor.server.plugins.di.DependencyResolverContext)
 */
public data class DependencyResolverContext(
    val resolver: DependencyResolver,
    val name: String,
) {
    /**
     * Property delegation for [DependencyResolverContext] for use with the `named` shorthand for string qualifiers.
     *
     * [Report a problem](https://ktor.io/feedback/?fqname=io.ktor.server.plugins.di.DependencyResolverContext.getValue)
     */
    public inline operator fun <reified T> getValue(thisRef: Any?, property: KProperty<*>): T =
        resolver.getBlocking(DependencyKey<T>(name))

    /**
     * Get the dependency from the map for the key represented by the type (and optionally, with the given name).
     *
     * [Report a problem](https://ktor.io/feedback/?fqname=io.ktor.server.plugins.di.DependencyResolverContext.resolve)
     */
    public suspend inline fun <reified T> DependencyResolver.resolve(key: String? = null): T =
        get(DependencyKey<T>(key))
}
