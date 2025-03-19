/*
 * Copyright 2014-2025 JetBrains s.r.o and contributors. Use of this source code is governed by the Apache 2.0 license.
 */

package io.ktor.server.plugins.di

import io.ktor.server.application.*
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
     * Indicate that the given dependency is required.
     *
     * This is ensured after `validate()` is called.
     */
    public fun require(key: DependencyKey)

    /**
     * Performs resolutions, ensures there are no missing dependencies.
     *
     * @throws DependencyInjectionException When there are invalid references in the configuration
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
    private val resolution: DependencyResolution,
    public override val reflection: DependencyReflection,
) : DependencyRegistry, DependencyProvider by provider {

    private val requiredKeys = mutableSetOf<DependencyKey>()
    private val resolver: Lazy<DependencyResolver> = lazy {
        resolution.resolve(provider, reflection)
    }

    override fun <T> set(key: DependencyKey, value: DependencyResolver.() -> T) {
        if (resolver.isInitialized()) throw OutOfOrderDependencyException(key)
        provider.set(key, value)
    }

    override fun <T : Any> get(key: DependencyKey): T =
        resolver.value.get(key)

    override fun <T : Any> getOrPut(key: DependencyKey, defaultValue: () -> T): T =
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
    DependencyResolution { provider, reflection ->
        val injector = ProcessingDependencyResolver(reflection, provider)
        MapDependencyResolver(reflection, injector.resolveAll())
    }

/**
 * A short-lived resolver for populating a map of instances.
 */
@Suppress("UNCHECKED_CAST")
public class ProcessingDependencyResolver(
    override val reflection: DependencyReflection,
    private val provider: DependencyProvider,
) : DependencyResolver {
    private val resolved = mutableMapOf<DependencyKey, Result<Any>>()
    private val visited = mutableSetOf<DependencyKey>()

    public fun resolveAll(): Map<DependencyKey, Result<Any>> {
        if (resolved.isNotEmpty()) return resolved.toMap()

        for (key in provider.declarations.keys) {
            get<Any>(key)
        }
        return resolved.toMap()
    }

    override fun <T : Any> get(key: DependencyKey): T =
        resolved.getOrPut(key) {
            if (!visited.add(key)) throw CircularDependencyException(listOf(key))
            try {
                val createFunction = provider.declarations[key]
                    ?: throw MissingDependencyException(key)
                Result.success(createFunction.create(this))
            } catch (cause: CircularDependencyException) {
                // Always throw when encountering with circular references,
                // capturing each key in the stack allows for better debugging
                throw CircularDependencyException(listOf(key) + cause.keys)
            } catch (cause: Throwable) {
                Result.failure(cause)
            }
        }.getOrThrow() as T

    override fun <T : Any> getOrPut(key: DependencyKey, defaultValue: () -> T): T =
        resolved.getOrPut(key) {
            runCatching {
                defaultValue()
            }
        }.getOrThrow() as T
}
