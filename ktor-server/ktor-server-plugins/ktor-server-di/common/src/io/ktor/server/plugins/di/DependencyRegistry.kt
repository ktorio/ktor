/*
 * Copyright 2014-2025 JetBrains s.r.o and contributors. Use of this source code is governed by the Apache 2.0 license.
 */

package io.ktor.server.plugins.di

import io.ktor.server.application.*
import io.ktor.utils.io.*

/**
 * Combined abstraction for dependency provider and resolver.
 */
@KtorDsl
public interface DependencyRegistry : DependencyProvider, DependencyResolver

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
}

/**
 * Standard [DependencyResolution] implementation, which populates a map of instances using
 * [ProcessingDependencyResolver].
 */
public val DefaultDependencyResolution: DependencyResolution =
    DependencyResolution { provider, reflection ->
        val injector = ProcessingDependencyResolver(reflection, provider)
        val instances = provider.declarations.keys.associateWith { key ->
            runCatching { injector.get(key) as Any }
        }
        MapDependencyResolver(reflection, instances)
    }

/**
 * A short-lived resolver for populating a map of instances.
 */
@Suppress("UNCHECKED_CAST")
public class ProcessingDependencyResolver(
    override val reflection: DependencyReflection,
    private val provider: DependencyProvider,
) : DependencyResolver {
    private val resolved = mutableMapOf<DependencyKey, Any>()
    private val visited = mutableSetOf<DependencyKey>()

    override fun <T : Any> get(key: DependencyKey): T =
        getOrPut(key) {
            if (!visited.add(key)) throw CircularDependencyException(key)
            val result = provider.declarations[key]?.create(this)
                ?: throw MissingDependencyException(key)
            result as T
        }

    override fun <T : Any> getOrPut(key: DependencyKey, defaultValue: () -> T): T =
        resolved.getOrPut(key, defaultValue) as T
}
