/*
 * Copyright 2014-2025 JetBrains s.r.o and contributors. Use of this source code is governed by the Apache 2.0 license.
 */

package io.ktor.server.plugins.di

import io.ktor.server.plugins.di.DependencyConflictResult.Ambiguous
import io.ktor.server.plugins.di.DependencyConflictResult.Conflict
import io.ktor.server.plugins.di.DependencyConflictResult.KeepNew
import io.ktor.server.plugins.di.DependencyConflictResult.KeepPrevious
import io.ktor.server.plugins.di.DependencyConflictResult.Replace
import io.ktor.util.logging.KtorSimpleLogger
import io.ktor.util.logging.Logger
import io.ktor.util.logging.trace
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
 * Basic call for providing a dependency, like `provide<Service> { ServiceImpl() }`.
 */
public inline fun <reified T> DependencyProvider.provide(
    name: String? = null,
    noinline provide: DependencyResolver.() -> T
) {
    set(DependencyKey(typeInfo<T>(), name), provide)
}

/**
 * Wraps the logic for creating a new instance of a dependency.
 *
 * Concrete types of this sealed interface are used to include some metadata regarding how they were registered.
 */
public sealed interface DependencyCreateFunction {
    public val key: DependencyKey
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
    public override val key: DependencyKey,
    private val init: DependencyResolver.() -> Any
) : DependencyCreateFunction {
    private var cached: Any? = null

    override fun create(resolver: DependencyResolver): Any =
        cached ?: init(resolver).also { cached = it }

    public fun derived(distance: Int): ImplicitCreateFunction =
        ImplicitCreateFunction(this, distance)
}

/**
 * Represents an implicitly registered dependency creation function that delegates to its explicit parent.
 *
 * @property origin The instance of [ExplicitCreateFunction] that this class delegates creation logic to.
 * @property distance The distance from the original key.
 */
public class ImplicitCreateFunction(
    public val origin: ExplicitCreateFunction,
    public val distance: Int,
) : DependencyCreateFunction {
    override val key: DependencyKey
        get() = origin.key

    override fun create(resolver: DependencyResolver): Any =
        origin.create(resolver)

    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other == null || this::class != other::class) return false

        other as ImplicitCreateFunction

        if (distance != other.distance) return false
        if (origin != other.origin) return false

        return true
    }

    override fun hashCode(): Int {
        var result = distance
        result = 31 * result + origin.hashCode()
        return result
    }
}

/**
 * Represents a specific implementation of [DependencyCreateFunction] that throws an exception
 * when there are multiple dependencies matching the given key, leading to an ambiguity.
 *
 * @property key The key for the dependency that caused the ambiguity.
 * @property keys A set of possible matching keys that caused the ambiguity.
 *
 * @throws AmbiguousException Always thrown when attempting to create a dependency
 * through the [create] method.
 */
public data class AmbiguousCreateFunction(
    public override val key: DependencyKey,
    val functions: Set<DependencyCreateFunction>
) : DependencyCreateFunction {
    public companion object {
        /**
         * Instantiate a new AmbiguousCreateFunction, if the provided functions are unique.
         *
         * This also will flatten any provided `AmbiguousCreateFunction`s.
         *
         * @param key The associated dependency key.
         * @param functions The functions to include in the resulting function.
         */
        public fun of(
            key: DependencyKey,
            vararg functions: DependencyCreateFunction
        ): DependencyCreateFunction {
            val functions = buildSet {
                for (function in functions) {
                    when (function) {
                        is AmbiguousCreateFunction -> addAll(function.functions)
                        else -> add(function)
                    }
                }
            }
            return functions.singleOrNull() ?: AmbiguousCreateFunction(key, functions)
        }
    }
    init {
        require(functions.size > 1) { "More than one function must be provided" }
    }

    override fun create(resolver: DependencyResolver): Any =
        throw AmbiguousDependencyException(key, functions.map { it.key })
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
    public val keyMapping: DependencyKeyCovariance = DefaultKeyCovariance,
    public val conflictPolicy: DependencyConflictPolicy = DefaultConflictPolicy,
    public val onConflict: (DependencyKey) -> Unit = { throw DuplicateDependencyException(it) },
    private val log: Logger = KtorSimpleLogger("io.ktor.server.plugins.di.MapDependencyProvider"),
) : DependencyProvider {
    private val map = mutableMapOf<DependencyKey, DependencyCreateFunction>()

    override val declarations: Map<DependencyKey, DependencyCreateFunction>
        get() = map

    override fun <T> set(key: DependencyKey, value: DependencyResolver.() -> T) {
        val create = ExplicitCreateFunction(key, value as DependencyResolver.() -> Any)
        trySet(key, create)
        insertCovariantKeys(create, key)
    }

    private fun trySet(key: DependencyKey, newFunction: DependencyCreateFunction) {
        when (val previous = map[key]) {
            null -> map[key] = newFunction
            else -> {
                map[key] = when (val result = resolveConflict(previous, newFunction)) {
                    Ambiguous -> AmbiguousCreateFunction.of(key, previous, newFunction)
                    Conflict -> throw DuplicateDependencyException(key)
                    KeepNew -> newFunction
                    KeepPrevious -> previous
                    is Replace -> result.function
                }
            }
        }
    }

    private fun resolveConflict(
        previous: DependencyCreateFunction,
        newFunction: DependencyCreateFunction
    ): DependencyConflictResult {
        val result = conflictPolicy.resolve(previous, newFunction)
        log.trace { "Conflicting keys: (${previous.key}, ${newFunction.key}) -> $result" }
        return result
    }

    private fun insertCovariantKeys(
        createFunction: ExplicitCreateFunction,
        key: DependencyKey
    ) {
        val covariantKeys = keyMapping.map(key, 0).toList()
        log.trace { "Inferred keys $key: $covariantKeys" }
        for ((key, distance) in covariantKeys) {
            trySet(key, createFunction.derived(distance))
        }
    }
}
