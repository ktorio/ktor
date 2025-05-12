/*
 * Copyright 2014-2025 JetBrains s.r.o and contributors. Use of this source code is governed by the Apache 2.0 license.
 */

package io.ktor.server.plugins.di

import io.ktor.server.application.*
import io.ktor.server.plugins.di.MutableDependencyMap.Companion.asResolver
import io.ktor.utils.io.*
import kotlin.properties.ReadOnlyProperty
import kotlin.reflect.KClass
import kotlin.reflect.KProperty

/**
 * A central registry for managing and resolving dependencies within a dependency injection context.
 *
 * The `DependencyRegistry` class acts as both a `DependencyProvider` and a `DependencyResolver`.
 * It facilitates the registration of dependencies through a `DependencyProvider` as well as the resolution
 * and validation of dependencies using the provided resolver mechanism. This registry also supports
 * reflective creation of instances and type-safe access to registered dependencies.
 *
 * @param provider The internal provider responsible for managing dependency initializers.
 * @param external A map of externally provided dependencies that can be used during resolution.
 * @param resolution The resolution mechanism used to create new instances of the `DependencyResolver`.
 * @param reflection A reflection implementation that supports dynamic instantiation of classes.
 */
@KtorDsl
public class DependencyRegistry(
    private val provider: DependencyProvider,
    private val external: DependencyMap,
    private val resolution: DependencyResolution,
    public override val reflection: DependencyReflection,
) : DependencyProvider by provider, DependencyResolver {

    internal val requirements = mutableMapOf<DependencyKey, Throwable>()
    private val resolver: Lazy<DependencyResolver> = lazy {
        resolution.resolve(provider, external, reflection)
    }

    override fun <T> set(key: DependencyKey, value: DependencyResolver.() -> T) {
        if (resolver.isInitialized()) throw OutOfOrderDependencyException(key)
        provider.set(key, value)
    }

    override fun contains(key: DependencyKey): Boolean =
        resolver.value.contains(key)

    override fun <T> get(key: DependencyKey): T =
        resolver.value.get(key)

    override fun <T> getOrPut(key: DependencyKey, defaultValue: () -> T): T =
        resolver.value.getOrPut(key, defaultValue)

    /**
     * Indicates that the given dependency is required.
     *
     * This is ensured after `validate()` is called.
     */
    public fun require(key: DependencyKey) {
        requirements += key to DependencyInjectionException()
    }

    /**
     * Performs resolutions, ensuring there are no missing dependencies.
     *
     * @throws DependencyInjectionException if there are invalid references in the configuration
     */
    public fun validate() {
        for ((key, ex) in requirements) {
            try {
                resolver.value.get<Any>(key)
            } catch (_: Throwable) {
                throw ex
            }
        }
    }

    /**
     * Get the dependency from the map for the key represented by the type (and optionally, with the given name).
     */
    public inline fun <reified T> resolve(key: String? = null): T =
        get(dependencyKey<T>(key))

    /**
     * Provides a delegated property for accessing a dependency from a [DependencyRegistry].
     * This operator function allows property delegation, ensuring the required dependency is
     * registered and retrievable through the registry.
     *
     * Example usage:
     * ```
     * val repository: Repository<Message> by dependencies
     * ```
     *
     * @param thisRef The receiver to which the property is being delegated. This parameter
     * is not used in the actual implementation.
     * @param prop The property for which the delegate is being requested.
     * @return A [ReadOnlyProperty] that provides access to the resolved dependency of type [T].
     * @throws DependencyInjectionException If the dependency required by [prop] is not resolvable
     * during access.
     */
    public inline operator fun <reified T> provideDelegate(
        thisRef: Any?,
        prop: KProperty<*>
    ): ReadOnlyProperty<Any?, T> {
        val key = dependencyKey<T>()
            .also(::require)
        return ReadOnlyProperty { _, _ ->
            get(key)
        }
    }

    /**
     * Provides an instance of the dependency associated with the specified [kClass].
     *
     * Uses the `create` method from the `DependencyResolver` to resolve and instantiate a dependency
     * of type [T] specified by the given [kClass].
     *
     * @param T The type of the dependency to be provided.
     * @param kClass The `KClass` representing the type of the dependency to be created or resolved.
     */
    public inline fun <reified T : Any> DependencyProvider.provide(kClass: KClass<out T>) {
        provide<T> { create(kClass) }
    }

    /**
     * Basic call for providing a dependency, like `provide<Service> { ServiceImpl() }`.
     */
    public inline fun <reified T> provide(name: String? = null, noinline provide: DependencyResolver.() -> T?) {
        set(dependencyKey<T>(name), provide)
    }
}

/**
 * DSL helper for declaring dependencies with `dependencies {}` block.
 */
@KtorDsl
public fun <T> Application.dependencies(action: DependencyRegistry.() -> T): T =
    dependencies.action()

public var Application.dependencies: DependencyRegistry
    get() {
        if (!attributes.contains(DependencyRegistryKey)) {
            install(DI)
        }
        return attributes[DependencyRegistryKey]
    }
    set(value) {
        attributes.put(DependencyRegistryKey, value)
    }

/**
 * Standard [DependencyResolution] implementation, which populates a map of instances using
 * [ProcessingDependencyResolver].
 */
public val DefaultDependencyResolution: DependencyResolution =
    DependencyResolution { provider, external, reflection ->
        val injector = ProcessingDependencyResolver(
            reflection,
            provider,
            external,
        )

        DependencyMapImpl(injector.resolveAll(), external)
            .asResolver(reflection)
    }

/**
 * A short-lived resolver for populating a map of instances.
 */
@Suppress("UNCHECKED_CAST")
public class ProcessingDependencyResolver(
    override val reflection: DependencyReflection,
    private val provider: DependencyProvider,
    private val external: DependencyMap,
) : DependencyResolver {
    private val resolved = mutableMapOf<DependencyKey, Result<*>>()
    private val visited = mutableSetOf<DependencyKey>()

    public fun resolveAll(): Map<DependencyKey, Result<*>> {
        if (resolved.isNotEmpty()) return resolved.toMap()

        provider.declarations.keys.forEach(::resolveKey)

        return resolved.toMap()
    }

    override fun contains(key: DependencyKey): Boolean =
        resolved.contains(key) || provider.declarations.contains(key) || external.contains(key)

    override fun <T> get(key: DependencyKey): T =
        resolveKey(key).getOrThrow() as T

    override fun <T> getOrPut(key: DependencyKey, defaultValue: () -> T): T =
        resolved.getOrPut(key) {
            runCatching {
                defaultValue()
            }
        }.getOrThrow() as T

    private fun resolveKey(key: DependencyKey): Result<*> =
        resolved.getOrPut(key) {
            if (!visited.add(key)) throw CircularDependencyException(listOf(key))
            try {
                val createFunction = provider.declarations[key]
                    ?: return@getOrPut getExternal(key)
                        ?: throw MissingDependencyException(key)
                Result.success(createFunction.create(this))
            } catch (cause: CircularDependencyException) {
                // Always throw when encountering with circular references,
                // capturing each key in the stack allows for better debugging
                throw CircularDependencyException(listOf(key) + cause.keys)
            } catch (cause: DependencyInjectionException) {
                Result.failure(cause)
            } catch (cause: Throwable) {
                Result.failure(DependencyInjectionException("Failed to instantiate `$key`", cause))
            }
        }

    private fun getExternal(key: DependencyKey): Result<Any>? =
        if (external.contains(key)) {
            Result.success(external.get(key))
        } else {
            null
        }
}
