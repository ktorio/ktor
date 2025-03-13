/*
 * Copyright 2014-2025 JetBrains s.r.o and contributors. Use of this source code is governed by the Apache 2.0 license.
 */

package io.ktor.server.plugins.di

import io.ktor.server.plugins.di.DependencyConflictResult.Ambiguous
import io.ktor.server.plugins.di.DependencyConflictResult.Conflict
import io.ktor.server.plugins.di.DependencyConflictResult.KeepNew
import io.ktor.server.plugins.di.DependencyConflictResult.KeepPrevious
import io.ktor.server.plugins.di.DependencyConflictResult.Replace
import io.ktor.util.reflect.*
import io.ktor.utils.io.*

/**
 * Represents a provider for managing dependencies in a dependency injection mechanism.
 * This interface allows the registration of dependency initializers and the retrieval
 * or instantiation of declared dependencies based on their unique keys.
 */
public interface DependencyProvider {
    /**
     * Associate the given dependency key with the corresponding initializer.
     *
     * @param T The type of the value produced by the initializer function.
     * @param key The key containing type and name
     * @param value The initialization script for the type
     * @throws DuplicateDependencyException If the `key` is already registered in the provider.
     */
    public fun <T> set(key: DependencyKey, value: DependencyResolver.() -> T)

    /**
     * A map containing registered dependency creation functions indexed by their unique dependency keys.
     *
     * This property holds the mapping between `DependencyKey` and `DependencyCreateFunction`.
     * Each entry associates a unique key that represents a type (and optionally, a name) with
     * a corresponding function responsible for creating the dependency instance.
     *
     * This is used internally by the dependency injection mechanism to retrieve or resolve dependencies
     * dynamically during runtime based on the registered initializers.
     */
    public val declarations: Map<DependencyKey, DependencyCreateFunction>
}

/**
 * Wraps the logic for creating a new instance of a dependency.
 *
 * Concrete types of this sealed interface are used to include some metadata regarding how they were registered.
 */
public sealed interface DependencyCreateFunction {
    public fun create(resolver: DependencyResolver): Any
}

/**
 * An explicit dependency creation function for directly registered types.
 *
 * This includes caching of the instance value so resolved covariant keys do not trigger the creation multiple times.
 *
 * @property key The unique identifier of the dependency associated with this creation function.
 * @property init A lambda that implements the creation logic for the dependency.
 */
public class ExplicitCreateFunction(
    public val key: DependencyKey,
    private val init: DependencyResolver.() -> Any
) : DependencyCreateFunction {
    private var cached: Any? = null

    override fun create(resolver: DependencyResolver): Any =
        cached ?: init(resolver).also { cached = it }

    public fun derived(): ImplicitCreateFunction =
        ImplicitCreateFunction(this)
}

/**
 * Represents an implicitly registered dependency creation function that delegates to its explicit parent.
 *
 * @property origin The instance of [ExplicitCreateFunction] that this class delegates creation logic to.
 */
public class ImplicitCreateFunction(public val origin: ExplicitCreateFunction) : DependencyCreateFunction {
    override fun create(resolver: DependencyResolver): Any =
        origin.create(resolver)
}

/**
 * Represents a specific implementation of [DependencyCreateFunction] that throws an exception
 * when there are multiple dependencies matching the given key, leading to an ambiguity.
 *
 * @property key The key for the dependency that caused the ambiguity.
 * @property keys A set of possible matching keys that caused the ambiguity.
 *
 * @throws AmbiguousDependencyException Always thrown when attempting to create a dependency
 * through the [create] method.
 */
public data class AmbiguousCreateFunction(
    val key: DependencyKey,
    val keys: Set<DependencyKey>
) : DependencyCreateFunction {
    override fun create(resolver: DependencyResolver): Any =
        throw AmbiguousDependencyException(key, keys)
}

/**
 * Retrieves the set of dependency keys associated with this creation function.
 *
 * For `ExplicitCreateFunction`, it returns a single key associated with the explicit registration.
 * For `ImplicitCreateFunction`, it returns the key of its origin `ExplicitCreateFunction`.
 * For `AmbiguousCreateFunction`, it returns all the keys that caused the ambiguity.
 *
 * @return A set of `DependencyKey` instances representing the dependency keys for this creation function,
 * depending on its specific implementation.
 */
public fun DependencyCreateFunction.keys(): Set<DependencyKey> =
    when (this) {
        is ExplicitCreateFunction -> setOf(key)
        is ImplicitCreateFunction -> setOf(origin.key)
        is AmbiguousCreateFunction -> keys
    }

/**
 * Executes the given block of code with the current instance if it is of type [ImplicitCreateFunction].
 *
 * @param block A lambda to be invoked if the current instance is of type [ImplicitCreateFunction].
 *              The lambda receives the instance as its argument.
 * @return The result of the lambda if the current instance is of type [ImplicitCreateFunction],
 *         or `null` if the current instance is not of the expected type.
 */
public fun <T> DependencyCreateFunction.ifImplicit(block: (ImplicitCreateFunction) -> T): T? =
    if (this is ImplicitCreateFunction) block(this) else null

/**
 * A dependency provider implementation that uses a map to store and manage dependencies.
 *
 * This class is designed to facilitate registration and resolution of dependencies within
 * a dependency injection system. It supports covariance through custom key mapping logic
 * and resolves conflicts according to a specified policy.
 *
 * @param keyMapping A mapping function that supports covariance by generating related keys for a dependency key.
 *                   For example, it may map a concrete type to its supertypes or related interfaces.
 * @param conflictPolicy A policy for handling conflicts when multiple initializers are registered
 *                       for the same dependency key.
 * @param onConflict A callback that is invoked when a conflict is encountered during registration.
 *                   By default, this throws a `DuplicateDependencyException`.
 */
@Suppress("UNCHECKED_CAST")
public open class MapDependencyProvider(
    public val keyMapping: DependencyKeyCovariance = Supertypes,
    public val conflictPolicy: DependencyConflictPolicy = DefaultConflictPolicy,
    public val onConflict: (DependencyKey) -> Unit = { throw DuplicateDependencyException(it) }
) : DependencyProvider {
    private val map = mutableMapOf<DependencyKey, DependencyCreateFunction>()

    override val declarations: Map<DependencyKey, DependencyCreateFunction>
        get() = map

    override fun <T> set(key: DependencyKey, value: DependencyResolver.() -> T) {
        val create = ExplicitCreateFunction(key, value as DependencyResolver.() -> Any)
        trySet(key, create)
        insertCovariantKeys(create, key)
    }

    private fun trySet(key: DependencyKey, value: DependencyCreateFunction) {
        when (val previous = map[key]) {
            null -> map[key] = value
            else -> {
                map[key] = when (val result = conflictPolicy.resolve(previous, value)) {
                    Ambiguous -> AmbiguousCreateFunction(key, previous.keys() + value.keys())
                    Conflict -> throw DuplicateDependencyException(key)
                    KeepNew -> value
                    KeepPrevious -> previous
                    is Replace -> result.function
                }
            }
        }
    }

    private fun insertCovariantKeys(
        createFunction: ExplicitCreateFunction,
        key: DependencyKey
    ) {
        val implicitCreateFunction = createFunction.derived()
        for (implicitKey in keyMapping.map(key)) {
            trySet(implicitKey, implicitCreateFunction)
        }
    }
}

/**
 * DSL helper for declaring dependencies with `dependencies {}` block.
 */
@KtorDsl
public operator fun DependencyProvider.invoke(action: DependencyProviderContext.() -> Unit) {
    DependencyProviderContext(this).action()
}

/**
 * Builder context for providing dependencies.
 */
@KtorDsl
public class DependencyProviderContext(
    private val delegate: DependencyProvider
) : DependencyProvider by delegate

/**
 * Basic call for providing a dependency, like `provide<Service> { ServiceImpl() }`.
 */
public inline fun <reified T> DependencyProvider.provide(
    name: String? = null,
    noinline provide: DependencyResolver.() -> T
) =
    set(DependencyKey(typeInfo<T>(), name), provide)
