/*
 * Copyright 2014-2025 JetBrains s.r.o and contributors. Use of this source code is governed by the Apache 2.0 license.
 */

package io.ktor.server.plugins.di

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
     * @param reflection The `DependencyReflection` instance used for reflective creation of dependency
     *                   instances.
     * @return A new instance of `DependencyResolver` configured with the provided arguments
     */
    public fun resolve(provider: DependencyProvider, reflection: DependencyReflection): DependencyResolver
}

/**
 * A map of object instances.
 */
public interface DependencyMap {
    public fun <T : Any> get(key: DependencyKey): T
    public fun <T : Any> getOrPut(key: DependencyKey, defaultValue: () -> T): T
}

/**
 * Extends [DependencyMap] with reflection, allowing for the automatic injection of types.
 */
public interface DependencyResolver : DependencyMap {
    public val reflection: DependencyReflection
}

/**
 * Basic implementation of [DependencyResolver] using a backing map.
 *
 * The map values are of the `Result` type so that we can ignore exceptions in the case of initialization problems for
 * types that are never referenced.
 */
@Suppress("UNCHECKED_CAST")
public class MapDependencyResolver(
    override val reflection: DependencyReflection,
    instances: Map<DependencyKey, Result<Any>>
) : DependencyResolver {
    private val map = instances.toMutableMap()

    override fun <T : Any> get(key: DependencyKey): T =
        (map[key] ?: throw MissingDependencyException(key)).getOrThrow() as T

    override fun <T : Any> getOrPut(key: DependencyKey, defaultValue: () -> T): T =
        map.getOrPut(key) { runCatching(defaultValue) }.getOrThrow() as T
}

/**
 * Decorates the dependency resolver with a qualified name for the expected type.
 *
 * Useful with delegation when used like: `val connection by dependencies.named("postgres")`
 */
public fun DependencyResolver.named(key: String) =
    DependencyResolverContext(this, key)

/**
 * Property delegation for [DependencyResolverContext] for use with the `named` shorthand for string qualifiers.
 */
public inline operator fun <reified T> DependencyResolverContext.getValue(thisRef: Any?, property: KProperty<*>): T =
    resolver.resolve(key)

/**
 * Context for property delegation with chaining (i.e., `dependencies.named("foo")`)
 */
public data class DependencyResolverContext(
    val resolver: DependencyResolver,
    val key: String,
)

/**
 * Get the dependency from the map for the key represented by the type (and optionally, with the given name).
 */
public inline fun <reified T> DependencyMap.resolve(key: String? = null): T =
    get(DependencyKey(typeInfo<T>(), key))
