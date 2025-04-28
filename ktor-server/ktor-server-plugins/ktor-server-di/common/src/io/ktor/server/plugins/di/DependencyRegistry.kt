/*
 * Copyright 2014-2025 JetBrains s.r.o and contributors. Use of this source code is governed by the Apache 2.0 license.
 */

package io.ktor.server.plugins.di

import io.ktor.server.application.*
import io.ktor.server.plugins.di.MutableDependencyMap.Companion.asResolver
import io.ktor.util.reflect.typeInfo
import io.ktor.utils.io.*
import kotlin.properties.ReadOnlyProperty
import kotlin.reflect.KProperty

/**
 * Combined abstraction for dependency provider and resolver.
 *
 * This is a stateful type that can verify that all required dependencies can be resolved.
 */
@KtorDsl
public interface DependencyRegistry : DependencyProvider, DependencyResolver {

    /**
     * Indicates that the given dependency is required.
     *
     * This is ensured after `validate()` is called.
     */
    public fun require(key: DependencyKey)

    /**
     * Performs resolutions, ensuring there are no missing dependencies.
     *
     * @throws DependencyInjectionException if there are invalid references in the configuration
     */
    public fun validate()
}

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
 * Basic implementation of [DependencyRegistry], which is a façade of [DependencyProvider] and a late-initialized
 * [DependencyResolver].  The resolver is available after the first call to a get function is made, which triggers the
 * [DependencyResolution] process to populate the instances.
 */
public class DependencyRegistryImpl(
    private val provider: DependencyProvider,
    private val external: DependencyMap,
    private val resolution: DependencyResolution,
    public override val reflection: DependencyReflection,
) : DependencyRegistry, DependencyProvider by provider {

    private val requiredKeys = mutableSetOf<DependencyKey>()
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

    override fun require(key: DependencyKey) {
        requiredKeys += key
    }

    override fun validate() {
        for (key in requiredKeys) {
            resolver.value.get<Any>(key)
        }
    }
}

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
public inline operator fun <reified T> DependencyRegistry.provideDelegate(
    thisRef: Any?,
    prop: KProperty<*>
): ReadOnlyProperty<Any?, T> {
    val key = DependencyKey(typeInfo<T>())
        .also(::require)
    return ReadOnlyProperty { _, _ ->
        this@provideDelegate.get(key)
    }
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
