/*
 * Copyright 2014-2025 JetBrains s.r.o and contributors. Use of this source code is governed by the Apache 2.0 license.
 */

package io.ktor.server.plugins.di

import io.ktor.server.plugins.di.DependencyConflictResult.*
import io.ktor.util.logging.*
import kotlinx.coroutines.coroutineScope
import kotlin.coroutines.CoroutineContext

/**
 * Represents a provider for managing dependencies in a dependency injection mechanism.
 * This interface allows the registration of dependency initializers and the retrieval
 * or instantiation of declared dependencies based on their unique keys.
 *
 * [Report a problem](https://ktor.io/feedback/?fqname=io.ktor.server.plugins.di.DependencyProvider)
 */
public interface DependencyProvider {
    /**
     * Associate the given dependency key with the corresponding initializer.
     *
     * [Report a problem](https://ktor.io/feedback/?fqname=io.ktor.server.plugins.di.DependencyProvider.set)
     *
     * @param T The type of the value produced by the initializer function.
     * @param key The key containing type and name
     * @param value The initialization script for the type
     * @throws DuplicateDependencyException If the `key` is already registered in the provider.
     */
    public fun <T> set(key: DependencyKey, value: suspend DependencyResolver.() -> T)
}

/**
 * Basic call for providing a dependency, like `provide<Service> { ServiceImpl() }`.
 *
 * [Report a problem](https://ktor.io/feedback/?fqname=io.ktor.server.plugins.di.provide)
 */
public inline fun <reified T> DependencyProvider.provide(
    name: String? = null,
    noinline provide: DependencyResolver.() -> T?
) {
    set(DependencyKey<T>(name), provide)
}

/**
 * A dependency provider implementation that uses a map to store and manage dependencies.
 *
 * This class is designed to facilitate registration and resolution of dependencies within
 * a dependency injection system. It supports covariance through custom key mapping logic
 * and resolves conflicts according to a specified policy.
 *
 * @param map A map containing the initial dependency values.
 *            By default, this is an empty map.
 * @param resolver A dependency resolver used for resolving dependencies.
 *                 By default, this uses a `MapDependencyResolver`.
 * @param keyMapping A mapping function that supports covariance by generating related keys for a dependency key.
 *                   For example, it may map a concrete type to its supertypes or related interfaces.
 * @param conflictPolicy A policy for handling conflicts when multiple initializers are registered
 *                       for the same dependency key.
 * @param onConflict A callback that is invoked when a conflict is encountered during registration.
 *                   By default, this throws a `DuplicateDependencyException`.
 * @param coroutineScope The coroutine scope used for asynchronous dependency resolution.
 *                       By default, this uses the global scope.
 * @param log A logger used for logging dependency resolution events.
 *            By default, this uses a `KtorSimpleLogger` with the name "io.ktor.server.plugins.di.MapDependencyProvider".
 *
 * @see DependencyKeyCovariance
 * @see DependencyConflictPolicy
 * @see DependencyResolver
 */
@Suppress("UNCHECKED_CAST")
internal class MapDependencyProvider(
    private val map: DependencyInitializerMap,
    private val keyMapping: DependencyKeyCovariance,
    private val conflictPolicy: DependencyConflictPolicy,
    private val onConflict: (DependencyKey) -> Nothing,
    private val log: Logger = KtorSimpleLogger("io.ktor.server.plugins.di.MapDependencyProvider"),
) : DependencyProvider {
    private companion object {
        private const val COVARIANT_LOG_LIMIT = 8
    }

    override fun <T> set(key: DependencyKey, value: suspend DependencyResolver.() -> T) {
        val create = DependencyInitializer.Explicit(key, value)
        log.debug { "Provided $key ${DependencyReference().externalTraceLine()}" }
        trySet(key, create)
        insertCovariantKeys(create, key)
    }

    private fun trySet(key: DependencyKey, newFunction: DependencyInitializer) {
        map[key] = when (val previous = map[key]) {
            null -> newFunction
            is DependencyInitializer.Missing -> newFunction.also(previous::provide)
            else -> when (val result = resolveConflict(previous, newFunction)) {
                Ambiguous ->
                    DependencyInitializer.Ambiguous.of(key, previous, newFunction)
                Conflict -> onConflict(key)
                KeepNew -> newFunction
                KeepPrevious -> previous
                is Replace -> result.function
            }
        }
    }

    private fun resolveConflict(
        previous: DependencyInitializer,
        newFunction: DependencyInitializer
    ): DependencyConflictResult {
        val result = conflictPolicy.resolve(previous, newFunction)
        log.trace { "Conflicting keys: (${previous.key}, ${newFunction.key}) -> $result" }
        return result
    }

    private fun insertCovariantKeys(
        createFunction: DependencyInitializer.Explicit,
        key: DependencyKey
    ) {
        val covariantKeys = keyMapping.map(key, 0).toList()
        log.trace { "Covariant keys: ${formatKeys(covariantKeys)}" }
        for ((key, distance) in covariantKeys) {
            trySet(key, createFunction.derived(distance))
        }
    }

    private fun formatKeys(keys: List<KeyMatch>): String =
        if (keys.size > COVARIANT_LOG_LIMIT) {
            keys.take(COVARIANT_LOG_LIMIT).joinToString { it.key.toString() } + ", ..."
        } else {
            keys.joinToString { it.key.toString() }
        }
}

/**
 * During initialization, we wrap every call with a safe resolver that prevents circular references.
 *
 * This is done by keeping track of the keys that are currently being resolved,
 * and throwing a `CircularDependencyException` if a cycle is detected.
 *
 * @param delegate The original resolver to be wrapped.
 * @param findOrigin A function that returns the original key for a given dependency key.
 * @param pending The set of keys that have already been resolved.
 *                This is used to track circular dependencies.
 */
internal class SafeResolver(
    val delegate: DependencyResolver,
    val pending: Set<DependencyKey>,
) : DependencyResolver {
    override val reflection: DependencyReflection
        get() = delegate.reflection
    override val coroutineContext: CoroutineContext
        get() = delegate.coroutineContext

    operator fun plus(key: DependencyKey) =
        SafeResolver(delegate, pending + key)

    override fun contains(key: DependencyKey): Boolean =
        delegate.contains(key)

    override fun getInitializer(key: DependencyKey): DependencyInitializer {
        return try {
            delegate.getInitializer(key)
        } catch (cause: CircularDependencyException) {
            throw CircularDependencyException(listOf(key) + cause.keys)
        }.also(::assertNoCycle)
    }

    override suspend fun <T> getOrPut(
        key: DependencyKey,
        defaultValue: suspend () -> T
    ): T {
        if (delegate.contains(key)) {
            assertNoCycle(delegate.getInitializer(key))
        }
        try {
            return delegate.getOrPut(key, defaultValue)
        } catch (cause: CircularDependencyException) {
            throw CircularDependencyException(listOf(key) + cause.keys)
        }
    }

    private fun assertNoCycle(init: DependencyInitializer) {
        if (init.originKey in pending) {
            throw CircularDependencyException(listOf(init.key))
        }
    }
}
