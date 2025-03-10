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

public sealed interface DependencyCreateFunction {
    public fun create(resolver: DependencyResolver): Any
}

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

public class ImplicitCreateFunction(public val origin: ExplicitCreateFunction) : DependencyCreateFunction {
    override fun create(resolver: DependencyResolver): Any =
        origin.create(resolver)
}

public data class AmbiguousCreateFunction(
    val key: DependencyKey,
    val keys: Set<DependencyKey>
) : DependencyCreateFunction {
    override fun create(resolver: DependencyResolver): Any =
        throw AmbiguousDependencyException(key, keys)
}

public fun DependencyCreateFunction.keys(): Set<DependencyKey> =
    when (this) {
        is ExplicitCreateFunction -> setOf(key)
        is ImplicitCreateFunction -> setOf(origin.key)
        is AmbiguousCreateFunction -> keys
    }

public fun <T> DependencyCreateFunction.ifImplicit(block: (ImplicitCreateFunction) -> T): T? =
    if (this is ImplicitCreateFunction) block(this) else null

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
                map[key] = when (val result = conflictPolicy(previous, value)) {
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
